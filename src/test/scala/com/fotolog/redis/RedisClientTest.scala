package com.fotolog.redis

import junit.framework.TestCase

import org.junit._
import Assert._
import Conversions._

class RedisClientTest extends TestCase {
    val c = RedisClient("localhost", 6380) // non-default port, just in case as this wipes out all data

    override def setUp() = super.setUp; c.flushall
    override def tearDown() = c.flushall

    def testPingGetSetExistsType() {
        assertTrue(c.ping)
        assertFalse(c.exists("foo"))
        assertTrue(c.set("foo", "bar"))
        assertTrue(c.exists("foo"))
        assertEquals("bar", c[String]("foo").get)
        assertEquals(KeyType.String, c.keytype("foo"))
        assertTrue(c.del("foo"))
        assertFalse(c.exists("foo"))
        assertFalse(c.del("foo"))
    }
    
    def testMgetMset() {
        assertTrue(c.set("foo" -> "foo1", "bar" -> "bar1", "baz" -> "baz1"))
        assertEquals(Seq(Some("foo1"), None, Some("bar1"), Some("baz1")), c.get[String]("foo", "blah", "bar", "baz"))
        assertEquals(Map("foo" -> "foo1", "bar" -> "bar1", "baz" -> "baz1"), c.mget[String]("foo", "blah", "baz", "bar"))
        assertTrue(c.set("foobar" -> "foo2"))
        assertEquals(Some("foo2"), c.get[String]("foobar"))
    }

    def testSetNxEx() {
        assertTrue(c.setnx("blah", "blah"))
        assertFalse(c.setnx("blah", "boo"))
        assertTrue(c.setnx("foobar" -> "foo2"))
        assertTrue(c.setnx("foo" -> "foo1", "bar" -> "bar1", "baz" -> "baz1"))
        assertFalse(c.setnx("xxx" -> "yyy", "bar" -> "blah"))
    }
    
    def testGetSet() {
        assertEquals(None, c.getset("foo", "bar"))
        assertEquals(Some("bar"), c.getset[String]("foo", "baz"))
        assertEquals(Some("baz"), c.getset[String]("foo", "blah"))
    }

    def testSetEx() {
        assertTrue(c.set("foo", 123, "bar"))
        assertEquals(Some("bar"), c.get[String]("foo"))
    }
    
    def testIncrDecr() {
        assertEquals(1, c.incr("foo"))
        assertEquals(3, c.incr("foo", 2))
        assertEquals(2, c.decr("foo"))
        assertEquals(-1, c.decr("foo", 3))
    }

    def testAppend() {
        assertTrue(c.set("foo", "bar"))
        assertEquals(6, c.append("foo", "baz"))
        assertEquals(Some("barbaz"), c.get[String]("foo"))
    }
    
    def testSubstr() {
        assertEquals(None, c.substr("foo", 0, 1))
        assertTrue(c.set("foo", "bar"))
        assertEquals(Some("ba"), c.substr[String]("foo", 0, 1))
    }

    def testExpirePersist() {
        assertTrue(c.set("foo", "bar"))
        assertTrue(c.expire("foo", 100))
        //assertTrue(c.persist("foo")) // depends on the version of redis
    }

    def testLists() {
        assertEquals(Seq(), c.lrange("foo", 0, 100))
        assertEquals(1, c.rpush("foo", "ccc"))
        assertEquals(2, c.lpush("foo", "bbb"))
        assertEquals(3, c.rpush("foo", "ddd"))
        assertEquals(4, c.lpush("foo", "aaa"))
        assertEquals(4, c.llen("foo"))
        assertEquals(Seq("aaa", "bbb", "ccc", "ddd"), c.lrange[String]("foo", 0, 100))
        assertEquals(Seq("bbb", "ccc"), c.lrange[String]("foo", 1, 2))
        assertTrue(c.ltrim("foo", 0, 2))
        assertEquals(Seq("aaa", "bbb", "ccc"), c.lrange[String]("foo", 0, 100))
        assertEquals(Some("bbb"), c.lindex[String]("foo", 1))
        assertEquals(None, c.lindex[String]("foo", 100))
        assertTrue(c.lset("foo", 1, "BBB"))
        assertEquals(Seq("aaa", "BBB", "ccc"), c.lrange[String]("foo", 0, 100))
        assertEquals(1, c.lrem[String]("foo", 10, "BBB"))
        assertEquals(Seq("aaa", "ccc"), c.lrange[String]("foo", 0, 100))
        assertEquals(Some("ccc"), c.rpop[String]("foo"))
        assertEquals(Seq("aaa"), c.lrange[String]("foo", 0, 100))
        assertEquals(2, c.rpush("foo", "bbb"))
        assertEquals(Some("aaa"), c.lpop[String]("foo"))
        assertEquals(Some("bbb"), c.lpop[String]("foo"))
        assertEquals(None, c.lpop[String]("foo"))
        assertEquals(None, c.rpop[String]("foo"))
        
        assertEquals(1, c.rpush("foo", "aaa"))
        assertEquals(Some("aaa"), c.rpoplpush[String]("foo", "foo1"))
        assertEquals(Seq("aaa"), c.lrange[String]("foo1", 0, 100))
    }

    def testHashes() {
        assertEquals(Map(), c.hgetall[String]("hk"))
        assertTrue(c.hset("hk", "foo", "bar"))
        assertEquals(Some("bar"), c.hget[String]("hk", "foo"))
        
        assertTrue(c.hmset("hk", "foo" -> "bar", "bar" -> "baz", "baz" -> "blah"))
        assertEquals(Map("foo" -> "bar", "bar" -> "baz", "baz" -> "blah"), c.hmget[String]("hk", "foo", "bar", "baz"))
        
        assertEquals(3, c.hlen("hk"))
        assertEquals(Seq("foo", "bar", "baz"), c.hkeys("hk"))
        assertEquals(Seq("bar", "baz", "blah"), c.hvals[String]("hk"))
        assertEquals(Map("foo" -> "bar", "bar" -> "baz", "baz" -> "blah"), c.hgetall[String]("hk"))

        assertEquals(1, c.hincr("hk", "counter"))
        assertEquals(3, c.hincr("hk", "counter", 2))
        
        assertTrue(c.hexists("hk", "foo"))
        assertTrue(c.hdel("hk", "foo"))
        assertFalse(c.hexists("hk", "foo"))
    }

    def testSets() {
        import scala.collection.{ Set => SCSet }
        assertTrue(c.sadd("set1", "foo"))
        assertFalse(c.sadd("set1", "foo"))
        assertTrue(c.srem("set1", "foo"))
        assertFalse(c.srem("set1", "foo"))

        assertTrue(c.sadd("set1", "foo"))
        assertEquals(Option("foo"), c.spop[String]("set1"))
        assertFalse(c.srem("set1", "foo"))


        assertTrue(c.sadd("set1", "foo"))
        assertTrue(c.smove("set1", "set2", "foo"))
        assertEquals(None, c.spop[String]("set1"))
        assertEquals(Option("foo"), c.spop[String]("set2"))

        assertEquals(0, c.scard("set1"))
        assertTrue(c.sadd("set1", "foo"))
        assertEquals(1, c.scard("set1"))
        assertTrue(c.sadd("set1", "bar"))
        assertEquals(2, c.scard("set1"))

        assertTrue(c.sismember("set1", "foo"))
        assertTrue(c.sismember("set1", "bar"))
        assertFalse(c.sismember("set1", "boo"))

        assertTrue(c.sadd("set1", "baz"))
        assertTrue(c.sadd("set2", "foo"))
        assertTrue(c.sadd("set2", "bar"))
        assertTrue(c.sadd("set2", "blah"))

        assertEquals(SCSet("foo", "bar", "baz"), c.smembers[String]("set1"))
        assertEquals(SCSet("foo", "bar", "blah"), c.smembers[String]("set2"))

        assertEquals(SCSet("foo", "bar"), c.sinter[String]("set1", "set2"))
        assertEquals(SCSet("foo", "bar", "baz", "blah"), c.sunion[String]("set1", "set2"))
        assertEquals(SCSet("baz"), c.sdiff[String]("set1", "set2"))
        assertEquals(SCSet("blah"), c.sdiff[String]("set2", "set1"))


        assertEquals(2, c.sinterstore("setX", "set1", "set2"))
        assertEquals(SCSet("foo", "bar"), c.smembers[String]("setX"))

        assertEquals(4, c.sunionstore("setX", "set1", "set2"))
        assertEquals(SCSet("foo", "bar", "baz", "blah"), c.smembers[String]("setX"))

        assertEquals(1, c.sdiffstore("setX", "set1", "set2"))
        assertEquals(SCSet("baz"), c.smembers[String]("setX"))

        assertTrue(SCSet("foo", "bar", "baz").contains(c.srandmember[String]("set1").get))
    }


    def testIntConversions(){
        testIntVals.foreach{ i=>
            assertTrue(c.set("foo", i))
            assertEquals(Some(i), c.get[Int]("foo"))
        }
        testLongVals.foreach{ i=>
            assertTrue(c.set("foo", i))
            assertEquals(Some(i), c.get[Long]("foo"))
        }
    }

    private def testIntVals(): List[Int] = 0 :: {for(i<-0 to 30) yield List(1<<i,-(1<<i))}.toList.flatten
    private def testLongVals(): List[Long] = 0l :: {for(i<-0 to 62) yield List(1l<<i,-(1l<<i))}.toList.flatten
}
