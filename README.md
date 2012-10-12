Lucene/Solr Hash-Based Index Splitter
=========

Author
======
Nolan Lawson

License
=======
[Apache 2.0][1]

Overview
========
When an index in Lucene/Solr gets too large and your queries get too slow, you need to split your index into "shards" which can be separately queried and combined. [The Lucene/Solr docs][3] recommend that you split up your index using a function like this:

```javascript
document.uniqueId.hashCode() % numShards
``` 

Well, that's fine if you're starting from scratch, but what if you already have an index that you want to split up?  This utility can help you out.

This is an extension of Lucene's [MultiPassIndexSplitter][2] that splits based on the md5sum of each document's id, mod the number of shards.  So for instance, say you want to split your index into 4 shards, and you have the following document IDs in your index:

```javascript
['MyTestDocument2',
    'MyTestDocument3',
    'MyTestDocument4',
    'MyTestDocument5',
    'MyTestDocument6',
    'MyTestDocument7',
    '日本語'] // utf8, means "Japanese" in Japanese

```

You'll end up with md5 sums of:

```javascript
[190451837140044158302779253469716410810,
    154736196455933244638815998723571563529,
    228857960587141625483496904830786656907,
    244407972157904876700286015798136753977,
    248469930502121775991513712645312516144,
    323520551662003182321302873795880831432,
    88491575051939950596574576699976684]
```

Modulo 4 for each gives us:

```javascript
[2, 1, 3, 1, 0, 0, 0]
```

...which means you'll end up with 3 documents in shard #0, 2 in shard #1, 1 in shard #2, and 1 in shard #3.

Usage
========

```
wget 'https://github.com/downloads/HON-Khresmoi/hash-based-index-splitter/hash-based-index-splitter-1.0.2.jar'
java -Xmx1G -jar hash-based-index-splitter-1.0.2.jar -out /path/to/my/output -num 4 /path/to/my/index
```

This will write 4 shards to /path/to/my/output:

```
Generating shard hashes...
[==================================================]   100%
Generated hashes.

Generating documents for shard 1 of 4...
[==================================================]   100%
Writing 3 document(s) to shard 1...
Wrote to shard 1.

Generating documents for shard 2 of 4...
[==================================================]   100%
Writing 2 document(s) to shard 2...
Wrote to shard 2.

Generating documents for shard 3 of 4...
[==================================================]   100%
Writing 1 document(s) to shard 3...
Wrote to shard 3.

Generating documents for shard 4 of 4...
[==================================================]   100%
Writing 1 document(s) to shard 4...
Wrote to shard 4.

Done.
```

The shards will be named part0, part1, part2, and part3.

It is assumed that your unique ID field is called "id".  If this is not the case, use the -idField parameter.

Full usage:

```
java -Xmx1G -jar hash-based-index-splitter-1.0.2.jar -out <outputDir> -num <numParts> [-idField idField] <inputIndex1> [<inputIndex2 ...]
	inputIndex        path to input index, multiple values are ok
	-out ouputDir     path to output directory to contain partial indexes
	-num numParts     number of parts to produce
	-idField idField  unique ID field name ("id" by default)
```

Generate shards from document IDs
=========

Here's how you can generate valid md5-based shard numbers in your favorite language:

Java:
```java
import org.apache.commons.codec.digest.DigestUtils;

public static int getShard(String id, int numShards) {
    BigInteger md5sum = new BigInteger(DigestUtils.md5Hex(id.getBytes("UTF-8")), 16);
    return md5sum.mod(BigInteger.valueOf(numShards)).intValue();
}
```

Perl:
```perl
use utf8;
use Digest::MD5 qw/md5_hex/;
use bignum qw/hex/;
use Encode qw(encode_utf8);

sub getShard {
    my ($id, $numShards) = @_;
    return (hex(md5_hex(encode_utf8($id))) % $numShards)->numify();
}
```

Python:
```python
import md5, sys
reload(sys)
sys.setdefaultencoding('utf-8')

def getShard(id,numShards):
    return int(int(md5.new(id).hexdigest(),16) % numShards)
```

Build this project
======

Simply run:

```
mvn assembly:single
````

[1]: http://www.apache.org/licenses/LICENSE-2.0.html
[2]: http://lucene.apache.org/core/old_versioned_docs/versions/3_5_0/api/all/org/apache/lucene/index/MultiPassIndexSplitter.html
[3]: http://wiki.apache.org/solr/DistributedSearch
