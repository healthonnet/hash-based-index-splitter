```Lucene/Solr Hash-Based Index Splitter
=========

Author
======
Nolan Lawson

License
=======
[Apache 2.0][1]

Overview
========
When an index in Lucene/Solr gets too large, you need to split it into "shards" which can be separately queried and combined. [The Lucene/Solr docs][3] recommend that you split up your index based on the document's ID's hash mod the number of shards.

Well, that works fine at indexing time, but what if you already have a large index that you want to split up?  This utility can help you out.

This is an extension of Lucene's [MultiPassIndexSplitter][2] that splits based on the md5sum of each document's id, mod the number of shards.  So for instance, say you want to split your index into 4 shards, and you have the following document IDs in your index:

```javascript
['MyTestDocument2','MyTestDocument3','MyTestDocument4','MyTestDocument5','MyTestDocument6','MyTestDocument7','日本語'] // last one is utf8, means "Japanese" in Japanese

```

You'll end up with md5 sums for each of:

```javascript
['190451837140044158302779253469716410810','154736196455933244638815998723571563529','228857960587141625483496904830786656907',
        '244407972157904876700286015798136753977','248469930502121775991513712645312516144','323520551662003182321302873795880831432',
        '88491575051939950596574576699976684']
```

Mod 4 for each gives us:

```javascript
[2,1,3,1,0,0,0]
```

...which means you'll end up with 3 documents in shard #0, 2 documents in shard #1, 1 document in shard #2, and 2 documents in shard #3.  The original index is not modified.

Usage
========

```
wget 'http://githubblahblahblah'
java -Xmx1G -jar hash-based-index-splitter-1.0.jar -out /path/to/my/output -num 4 /path/to/my/index
```

This will write 4 shards to /path/to/my/output:

```
Writing 334 documents to part 1 ...
Writing 330 documents to part 2 ...
Writing 351 documents to part 3 ...
Writing 322 documents to part 4 ...
Done.
```

The shards will be named part0, part1, part2, and part3.

Valid hashes
=========

For reference, you can generate valid md5 hashes of the UTF8 string id using your favorite language like this:

Java:
```java
import org.apache.commons.codec.digest.DigestUtils;
String id = getMyIdSomehow();
BigInteger md5sum = new BigInteger(DigestUtils.md5Hex(id.getBytes("UTF-8")), 16);
int shard = hash.mod(BigInteger.valueOf(numShards)).intValue();
```

Perl:
```perl
use utf8;
use Digest::MD5 qw/md5_hex/;
use bignum qw/hex/;
use Encode qw(encode_utf8);

my $id = getMyIdSomehow();
my $shard = hex(md5_hex(encode_utf8($_))) % $numShards;
```

[1]: http://www.apache.org/licenses/LICENSE-2.0.html
[2]: http://lucene.apache.org/core/old_versioned_docs/versions/3_5_0/api/all/org/apache/lucene/index/MultiPassIndexSplitter.html
[3]: http://wiki.apache.org/solr/DistributedSearch
