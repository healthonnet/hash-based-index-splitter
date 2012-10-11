package org.hon.lucene.hashbasedindexsplitter;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.MultiPassIndexSplitter;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.ReaderUtil;
import org.apache.lucene.util.Version;

public class HashBasedIndexSplitter extends MultiPassIndexSplitter {

    /**
     * Split source index into multiple parts, using the hash of the document id
     * to accomplish this.
     * 
     * E.g. if there are 4 target directories and a document's hash % 4 == 0,
     * then it'll go in the first directory. If it's 3, then it'll go in the
     * last directory, etc.
     * 
     * Largely copied from parent class, except I ignore the 'seq' value and use
     * the hash % numShards as described above.
     * 
     * @param input
     *            source index, can be read-only, can have deletions, can have
     *            multiple segments (or multiple readers).
     * @param outputs
     *            list of directories where the output parts will be stored.
     * @param seq
     *            (ignored) increasing ranges of document id-s. If false, source
     *            document id-s will be assigned in a deterministic round-robin
     *            fashion to one of the output splits.
     * @throws IOException
     */
    public void split(Version version, IndexReader input, Directory[] outputs, boolean seq) throws IOException {
        if (outputs == null || outputs.length < 2) {
            throw new IOException("Invalid number of outputs.");
        }
        if (input == null || input.numDocs() < 2) {
            throw new IOException("Not enough documents for splitting");
        }
        int numParts = outputs.length;
        // wrap a potentially read-only input
        // this way we don't have to preserve original deletions because neither
        // deleteDocument(int) or undeleteAll() is applied to the wrapped input
        // index.
        input = new FakeDeleteIndexReader(input);
        int maxDoc = input.maxDoc();
        int partLen = maxDoc / numParts;
        // assume that the id is called "id"
        FieldSelector fieldSelector = new SetBasedFieldSelector(
                Collections.singleton("id"),
                Collections.<String>emptySet());
        for (int i = 0; i < numParts; i++) {
            int count = 0;
            input.undeleteAll();

            // hash-based round-robin
            for (int j = 0; j < maxDoc; j++) {
                Document document = input.document(j, fieldSelector);
                String id = document.get("id");
                BigInteger hash = new BigInteger(DigestUtils.md5Hex(id.getBytes("UTF-8")), 16);
                if (hash.mod(BigInteger.valueOf(numParts)).intValue() != i) {
                    input.deleteDocument(j);
                } else {
                    count++;
                }
            }
            IndexWriter w = new IndexWriter(outputs[i], new IndexWriterConfig(version, new WhitespaceAnalyzer(
                    Version.LUCENE_CURRENT)).setOpenMode(OpenMode.CREATE));
            System.err.println("Writing " + count + " document(s) to part " + (i + 1) + " ...");
            w.addIndexes(input);
            w.close();
        }
        System.err.println("Done.");
    }

    /**
     * Copied from parent class.
     * 
     * @author nolan
     * 
     */
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err
                    .println("Usage: HashBasedIndexSplitter -out <outputDir> -num <numParts> [-seq] <inputIndex1> [<inputIndex2 ...]");
            System.err.println("\tinputIndex\tpath to input index, multiple values are ok");
            System.err.println("\t-out ouputDir\tpath to output directory to contain partial indexes");
            System.err.println("\t-num numParts\tnumber of parts to produce");
            System.exit(-1);
        }
        ArrayList<IndexReader> indexes = new ArrayList<IndexReader>();
        String outDir = null;
        int numParts = -1;
        boolean seq = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-out")) {
                outDir = args[++i];
            } else if (args[i].equals("-num")) {
                numParts = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-seq")) {
                seq = true;
            } else {
                File file = new File(args[i]);
                if (!file.exists() || !file.isDirectory()) {
                    System.err.println("Invalid input path - skipping: " + file);
                    continue;
                }
                Directory dir = FSDirectory.open(new File(args[i]));
                try {
                    if (!IndexReader.indexExists(dir)) {
                        System.err.println("Invalid input index - skipping: " + file);
                        continue;
                    }
                } catch (Exception e) {
                    System.err.println("Invalid input index - skipping: " + file);
                    continue;
                }
                indexes.add(IndexReader.open(dir, true));
            }
        }
        if (outDir == null) {
            throw new Exception("Required argument missing: -out outputDir");
        }
        if (numParts < 2) {
            throw new Exception("Invalid value of required argument: -num numParts");
        }
        if (indexes.size() == 0) {
            throw new Exception("No input indexes to process");
        }
        File out = new File(outDir);
        if (!out.mkdirs()) {
            throw new Exception("Can't create output directory: " + out);
        }
        Directory[] dirs = new Directory[numParts];
        for (int i = 0; i < numParts; i++) {
            dirs[i] = FSDirectory.open(new File(out, "part-" + i));
        }
        HashBasedIndexSplitter splitter = new HashBasedIndexSplitter();
        IndexReader input;
        if (indexes.size() == 1) {
            input = indexes.get(0);
        } else {
            input = new MultiReader(indexes.toArray(new IndexReader[indexes.size()]));
        }
        splitter.split(Version.LUCENE_CURRENT, input, dirs, seq);
    }

    /**
     * Copied from parent class.
     * 
     * @author nolan
     * 
     */
    private static final class FakeDeleteIndexReader extends MultiReader {

        public FakeDeleteIndexReader(IndexReader reader) throws IOException {
            super(initSubReaders(reader));
        }

        private static IndexReader[] initSubReaders(IndexReader reader) throws IOException {
            final ArrayList<IndexReader> subs = new ArrayList<IndexReader>();
            new ReaderUtil.Gather(reader) {
                @Override
                protected void add(int base, IndexReader r) {
                    subs.add(new FakeDeleteAtomicIndexReader(r));
                }
            }.run();
            return subs.toArray(new IndexReader[subs.size()]);
        }

    }

    /**
     * Copied from parent class.
     * 
     * @author nolan
     * 
     */
    private static final class FakeDeleteAtomicIndexReader extends FilterIndexReader {
        FixedBitSet dels;
        FixedBitSet oldDels;

        public FakeDeleteAtomicIndexReader(IndexReader in) {
            super(in);
            assert in.getSequentialSubReaders() == null;
            dels = new FixedBitSet(in.maxDoc());
            if (in.hasDeletions()) {
                oldDels = new FixedBitSet(in.maxDoc());
                for (int i = 0; i < in.maxDoc(); i++) {
                    if (in.isDeleted(i))
                        oldDels.set(i);
                }
                dels.or(oldDels);
            }
        }

        @Override
        public int numDocs() {
            return in.maxDoc() - dels.cardinality();
        }

        /**
         * Just removes our overlaid deletions - does not undelete the original
         * deletions.
         */
        @Override
        protected void doUndeleteAll() throws CorruptIndexException, IOException {
            dels = new FixedBitSet(in.maxDoc());
            if (oldDels != null) {
                dels.or(oldDels);
            }
        }

        @Override
        protected void doDelete(int n) throws CorruptIndexException, IOException {
            dels.set(n);
        }

        @Override
        public boolean hasDeletions() {
            return in.maxDoc() != this.numDocs();
        }

        @Override
        public boolean isDeleted(int n) {
            return dels.get(n);
        }

        @Override
        public IndexReader[] getSequentialSubReaders() {
            return null;
        }

        @Override
        public TermPositions termPositions() throws IOException {
            return new FilterTermPositions(in.termPositions()) {

                @Override
                public boolean next() throws IOException {
                    boolean res;
                    while ((res = super.next())) {
                        if (!dels.get(doc())) {
                            break;
                        }
                    }
                    return res;
                }
            };
        }
    }

}
