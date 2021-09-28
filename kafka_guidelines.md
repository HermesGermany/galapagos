# Apache Kafka Guidelines

Based on the [Event-Driven Architecture Principles](event_driven_architecture_principles.md), we established an 
_opinionated_ Ruleset for Apache Kafka. Kafka, out of the box, can be used in a variety of ways, and there is no simple 
"set of settings" which enforces our principles for our desired Architecture approach.

This Ruleset here is written **without** a reference to Galapagos, but, as you will quickly recognize, they would be 
hard to enforce without an appropriate toolset as support. We use Galapagos as this toolset.

## Topic Types

We introduce **four** types of topics, depending on their intended usage. On the first level, there is a split between
_API_ and _internal_ topics. This acknowledges the fact that most teams will want to use such "cool thing" as a central
Kafka also for their internal, technical-driven tasks. The API topics then split into three sub-types.

### Internal Topics

Internal topics may be used **only within the same application** (but e.g. between different deployment components of
the same application). They usually have a technical driver like a log, some kind of technical command pattern or similar.

For internal topics, only very few additional rules apply. Teams are free on what to put onto these topics (including
binary data, if desired), and can freely create and delete these topics (of their own application, of course).

### Event Topics

This is the most important topic type to support the 
[Event-Driven Architecture Principles](event_driven_architecture_principles.md). An **Event** topic usually has only
**one** (logical) producer, but several consumers (technically, producers can of course be scaled to multiple instances).

An Event Topic represents exactly **one** Business Event type. **Every** instance of this event type must be represented
by exactly **one** message on this topic.

The **name** of the Topic must contain the associcated _Business Capability_ and a descriptive name of the Business
Event Type, but **must not** contain the name of the producing application. A detailed per-company guideline is 
recommended on how to exactly build a Topic Name, e.g. `com.myacme.sales.OrderReceived` or `sales.order-received`.
Further (business-driven) sub-groupings after the Business Capability name are accepted.

Rules for the data formats on Event Topics apply. See _Data Formats_ for details.

### Data Topics

Data topics are a kind of "convenience special case" of an Event Topic for company-wide Master Data. They reflect the
Business Event Type "Master Data of Type XY has changed". Usually, they make use of _Log Compaction_, so, for a defined
amount of time, the _history_ of a single Master Data object is known, but afterwards, only the _latest_ state of the
object is stored (unlimited). To delete a Master Data object, it is **recommended** to provide some kind of "deletion"
marker in the used data format, so a deletion is represented by publishing a record on this topic with the deletion
marker set to `true`. You can also use Kafka's _tombstone_ markers if you are familiar with the concept, its side
effects, and required topic settings.

Most rules of Event Topics also apply to Data Topics, but the _Data Format_ requirements are a bit relaxed. Also, 
instead of a descriptive event name, they shall contain the name of the Master Data object type, e.g. 
`com.myacme.sales.Customers`.

Like on Event Topics, each data topic must contain **all** instances of the named Master Data object. If this is not
possible, the name must reflect this accordingly, e.g. `com.myacme.sales.PrivateCustomers`.

Contrary to Event Topics, the application producing onto a Data topic usually also is the _Owner_ of the referenced
Master Data object type.

### Commands

Command topics should rarely be used in a clean Event-Driven Architecture - nevertheless, there may be the need for them
in some cases. They represent the "inversion" of Event Topics, as they usually have **many** (different) producers and
only **one** (logical) consumer. A common use-case is _Send e-mail to..._. 

The driver for Command Topics may be of business **or** technical nature. 

Contrary to Event and Data topics, Command topics usually are associated with the receiving **application**, so they
should contain the application name within the topic name, e.g. `com.myacme.fancymailsender.SendMail`. 

As mentioned, Command topics should be used with caution in an Event-Driven Architecture. E.g. instead of sending e-mails
explicitly from multiple applications, you should better establish a "notification application" which reacts to business
events which make a notification (by e-mail, SMS, or whatever) necessary. This pattern also allows adding new 
communication channels with customers (e.g. push notification) without having to change multiple applications.

## Data Formats

The messages on each topic shall be easily consumable by a variety of consumers, without having to add complex or special
libraries like Apache Avro. As we assume that the number of business events per time is, in most cases, much lower than
for technical use cases like IoT logs or similar, we think that the tradeoff of using **JSON**, an uncompressed text 
format, instead of byte-optimized Avro format, is acceptable. This is still valid for millions of messages per topic
and day, as proven in practice. JSON allows consumers to process messages easily.

To make sure relevant information can be found in each message, the relatively new 
[CloudEvents](https://github.com/cloudevents/spec/blob/v1.0/spec.md) format shall be used on **Event** and **Command** 
Topics. It shall also be **considered** for Data topics, but here, the providers are free to choose their very own JSON 
format, if the properties of the CloudEvent specification do not match their needs. 

The **Payload** of the CloudEvents structure shall match a **JSON Schema** provided by the owning application of the
Business Event. This JSON Schema must be made available to all (potential) consumers and may only change in a
_consumer-compatible_ way.

## Topic Ownership

Each topic shall be owned by exactly one **application**. The ownership may change over time if an application is 
replaced. Also, there **may** be multiple (different) applications writing to the same topic (although this should be
a rare exception, see below). Still, **one** owner (application) must have the responsibility for the topic and 
coordinate changes with all producing applications. This owner is also the first contact for queries by consumers.

As outlined in the [Event-Driven Architecture Principles](event_driven_architecture_principles.md), an Event topic must
contain **all** instances of the Business Event type referenced by the topic name. So it is highly **recommended** that,
even if two different applications _seem_ to generate the same Business Event type, a discriminator is found to allow
for separate topics for the two applications. Example: If one application receives all Online orders, and another one
receives all Orders made by phone, consider introducing two topics, `sales.OnlineOrderReceived` and 
`sales.PhoneOrderReceived`. A third "application" (which may just be a script e.g. within Apache Nify) then could 
combine these two into a new topic, `sales.OrderReceived`; this would be perfectly valid within the Event-Driven
Architecture.

Note that this pattern has the nice implication that the third "application" contains (owns) the "Business Logic" which
event types make up the "combined" event type `OrderReceived`. When a new sale channel is introduced (of course, 
together with a new application providing a new Business Event type), all you have to do is adjust this application 
(maybe just a script). Applications subscribed to `sales.OrderReceived` will receive the orders from the new
sale channel without any adjustment being necessary.

## Topic Subscription

To be able to read from a published topic, an application must _subscribe_ to this topic. This does, in this context,
**not** refer to the technical process of connecting to the Kafka broker and be informed about new messages, but means
that an application must explicitly state that it wants to read from a given topic, for which, in turn, it gets the
required _Access Control List_ (ACL) entries in the Kafka broker to be able to do so.

This gives a high level of transparency of communication flows within the company, and gives Topic owners a hint if
changes to a topic have impact on others or not (see also next section).

## Topic Lifecycle

Usually, a company has multiple _Stages_ for their application landscape, e.g. `DEV`, `TEST`, and `PROD`. Following
these guidelines, DevOps teams may only create and change their topics on the **first** of these stages. Every change
can only be propagated to later stages following an automatic, reliable _Staging_ process, ensuring that every consumer
had a chance to _test_ changes on an integration environment before it goes live. Also, this ensures that no changes
are applied "on-the-fly" to a production environment (but not to lower stages) which are overriden by the next "formal"
staging.

*Deletion* of (published API) topics has to take the other direction, to ensure that no consumer loses a critical
resource without prior notice. First of all, all _subscribers_ (see previous section) have to cancel their subscription
of the topic explicitly (which also means their associated ACLs are removed). This is an explicit statement that they do
no longer rely on this resource. Once a topic in production stage does no longer have subscribers, it is _eligible_ for
deletion by the topic owner.

Would the deletion be propagated the other way, consumers could lose their critical resource in production as soon as
the staging is performed, although they may have already adapted in an integration stage. There would be no way for the
topic owners to "know" when it is safe to stage the topic deletion to production.

Of course, it seems impractical to get all consumers to "leave" the topic which the owner wants to delete. This is why
topics shall be marked as **deprecated** as soon as a deletion is desired. The deprecation mark shall include an _EOL_
date until which the topic is guaranteed to exist (the EOL date must, of course, give consumers enough time to adapt).
Consumers shall be notified automatically about the deprecation of a topic, and after the EOL date, topic owners are
allowed to delete the topic even if there are still subscribers.

As a nice side effect, if consumers adapt faster than the EOL date, the topic still may be deleted before the EOL date
if no more subscribers for the topic are registered.

## Consumer Groups

Each Consumer Group must at least contain the name of the consuming **application**. Further sub-groupings after the
application name are acceptable. Examples for consumer group name schemas could include 
`com.myacme.fancy-stuff-processor.order-logger` or `FancyStuffProcessor.internalComponent1.OrderLogger`.
A more detailed per-company guideline on consumer group names should be documented.

## Security

### Encryption / Authentication / Authorizaion

To ensure privacy and data security considerations, **all** communication with the Apache Kafka cluster serving topics 
like described above **must** be encrypted, i.e., use TLS encryption. Also, a reliable **authentication** machanism 
must be used to assign each **application** exactly one _identity_ (Kafka user name) on **each** cluster.
Developers **may** receive special authentication tokens with limited associated rights and / or limited lifetime of
the authentication token, e.g. for analyzing issues directly on the cluster with special tools.

The rights for each application shall be configured explicitly using Apache Kafka's _ACLs_. Every application must only
have the rights required for accessing configured topics, e.g.

* Write access to owned topics
* Read access to subscribed topics
* Full access to the prefix for their _internal_ topics
* Access to the prefix for their _consumer group IDs_.

### Sensitive data

Topics containing sensitive data (e.g. personal data) shall be marked as "protected" and may only be subscribed to after
a permission of the providing application team. Additional per-company rules or requirements by law may apply.
