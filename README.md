# Infinispan JDBC Driver

This project is a implementation of JDBC Driver for Infinispan distributed in-memory key/value store based on Teiid (https://teiid.io) technology.

If you are thinking, a JDBC driver for key/value store? yes, that is correct, using this driver, you can access Infinispan cluster as you would access Oracle, Postgres, MySQL etc. You could readily use this driver with any BI tools to analyze the data inside Infinispan cluster. You could use this driver to query/insert/update/delete rows to/from Infinispan Cache. Please note, that when you modify data using this driver (insert, update, delete) it will be using the same internal object format as your custom application that is written using Protobuf mechanism.
 
Since this driver is simple wrapper around the Teiid technology, you have all SQL query support that Teiid provides, even if some of the support is not natively supported by Infinispan. You can use huge library of Teiid functions. However note that since pushdown support is not fully available, some of these functions are being executed locally to put the results together, that means execution times may be higher. Hopefully in future releases, we will write enough distributed execution functions to process them remotely on Infinispan Cluster. 
 
### Limitations
This implementation does come with few limitations.
 * Only works with Remote Inifinispan over HotRod client. No, library mode.
 * Only works when you defined a "protobuf" file to work with your Cache contents. If are using freestyle objects, this will not work. (it should be also fairly simple to come with with DDL based schema)
 * Insert/Update/Delete only works when the Protobuf message has additional annotation to mark a column as Identity (@Id) column. See more details in "Enhancing Protobuf Metadata" section.
 * There can be only single top level message in your Protobuf file for single cache in Infinispan. See "Enhancing Protobuf Metadata" to define a way tie protobuf's message to cache such that more than single top level message can be defined in a single protobuf file.
 * Due to inherent nature of nested documents in the Infinispan cache, if a message has child(ren) messages, when parent is deleted all the children are cascade deleted also.
 
# JAVA Example

If you are working with maven add the below dependency to your project
```
Maven co-ordinates to come after first version is published
```  
Then you can use the code fragment like below to make connection to the Infinispan Cache.

```
Class.forName("io.infinispan.jdbc.Driver");
Connection conn = DriverManager.getConnection("jdbc:infinispan://localhost:11222/addressbook_indexed;protobuf=/quickstart/addressbook.proto");
Statement statement = conn.createStatement();
ResultSet resultSet = statement.executeQuery("select id, name, email from Person");
writeResultSet(resultSet);
```
 
## Enhancing Protobuf Metadata
When you define your cache content structure in the Protobuf format, there are many different limitations. By defining few annotations below, you can solve some of these issues.

### No Identity Key
When you define a message in Protobuf, there is no way to define which field of the message should be used as the key  store in Infinispan cache. This is typically left out to client applications to define. The below defines an annotation `@Id` that used by this driver when doing any updates to cache. Add this annotation to all the top level messages in Protobuf. For example:

```
message Person {

   required string name = 1;
   /* @Id */
   required int32 id = 2;

   optional bytes picture = 3;
}
```
Note: This is way Teiid (this Driver) choose to define the Key declaratively. 

### Single Top Level Message, Can not define multiple top level messages in given Protobuf file  
If you have single top level message in a given Protobuf file per Cache, then this does not affect you. However if you want to define multiple domain messages in a single Protobuf file, but they need to be stored in different Infinispan caches, you can use @Cache annotation to do so. If the defined Cache is not configured one will be created automatically. For example:   

```
/* @Cache(name=PersonCache)
message Person {

   required string name = 1;
   /* @Id */
   required int32 id = 2;

   optional bytes picture = 3;
}
```
Note: PersonCache from above is name of the Infinispan cache the contents of the Person object stored or read from. If you are working with an custom application, then that application defines programmatically where it reads/writes from. Since there is no code in this model, this is way Teiid choose to define this property declaratively.  

### Data Types
Protobuf defines a very limited set of data types namely int32, int64, float, double, string, bool, bytes. However typically JDBC  applications have more rich in types like Date, Timestamp, XML, Blobs, Geography etc. This driver provides support for @Teiid annotation to decorate a Protobuf's message field such that they can be read as rich data types through this driver. For example to read a "bytes" field as "blob" you can define as

```
/* @Cache(name=PersonCache)
message Person {

   required string name = 1;
   /* @Id */
   required int32 id = 2;

   /* @Teiid(type=blob) */
   optional bytes picture = 3;
}  
```
Similarly if you want to read `int64` as `timestamp` then define

```
  @Teiid(type=timestamp)
  int64 dateofbirth;  
```
Now when using the above driver, the `dateofbirth` column will be represented as `timestamp` and read/updated as `int64`. For full list of data types checkout http://teiid.github.io/teiid-documents/master/content/reference/Supported_Types.html. Please note that if you were using Java to define marshalers most of these mappings will map one to one, but be cautious not writing different formats when using the custom applications VS using this driver.
