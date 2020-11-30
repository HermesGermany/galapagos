# Principles for Event-Driven Architecture

We (the creators of Galapagos) strongly believe that an _Event-Driven Architecture_ is the best approach to solve common
problems which arise in most of today's companies with some kind of distributed IT, especially when introducing DevOps or 
crossfunctional teams and reducing or removing central IT Governance functions.
 
The following is a list of principles we want to enforce in our company to establish such an Event-Driven Architecture.
They shall support our overall company goals like cost reduction, reduction of time-to-market, but also ensure a high 
level of reliability.

We formulated these principles almost completely in a technology-neutral way. Only in some examples or practical 
additions, we refer to the underlying technology. 

Based on these principles, we selected _Apache Kafka_ as the technological base for us, and created _Galapagos_ as the
utility to enforce these principles while freeing DevOps teams from working through all of this stuff.

## Business Events instead of Business Objects

Classic inter-application communication channels in companies usually exchange business _Objects_, not _Events_. This
leads to data duplication and reduncancy, different levels of "truth" about the very same business object, and unclear
responsibilities and ownerships of data. 

Application teams tend to get their required data from the first application they can find which provides this data and 
which is cooperative enough to build them an interface (or giving them information about an existing interface they can 
use).

This tends to lead to long, complex, intransparent flows of data within the company, and makes changes to business
processes hard and error-prone.

By changing the inter-application communication to be based on Business _Events_, we avoid lots of this trouble. A
Business Event usually originates in exactly one application, which in this case **is** the owner of this Event (but
not necessarily of the associated Business Object). Parts (or all) of the associated current state of the business 
object may still be attached to the Event as the _Event Payload_, so recipients of the events do not have to look this 
up in other systems.

To avoid _Event Cumulation_ (i.e. an application has to gather **all** events for a Business Object type to know the
current status) and Business Logic duplication (i.e. an application has to know how to interpret the events to derive
the current status of the Business Object), central information-caching applications can be established. These are the
natural "owners" of the corresponding Business Object type and can answer all kinds of questions about the Business
Objects e.g. via a provided REST API. They collect all corresponding events and "know" how to interpret them.


## Domains instead of Applications

Although most IT people think in applications, Business Events do not _really_ belong to an application. Instead, they
belong to a _Business Domain_ (see _Domain Driven Design_). Applications come and go, will be replaced, split, whatever.
But the Business Events usually exist until some fundamental change in the underlying _Business Process_ is done.

So, Applications **do** _own_ (generate) Business Events for the time of their existence, but the Event must logically
be bound to a Business Domain.

This shall reflect also in the **naming** of the Event type. An event like _Order Received_ may mean something completely
different to the Sales domain than to the Internal Logistics domain. So, when referring to the Event type, the name of 
the owning Business Domain must be included.

## Event Types are complete, or are not at all

A published Business Event Type must contain **all** events (event instances) of this Type, or it is not a valid Event
Type in the context of these principles. An Event Type _Order Received_ (from the Business Domain "Sales") is not valid
if it contains only the Business Events of Orders received via an online channel, but not e.g. the orders received via
a callcenter. If an application can provide only a subset, the Event Type must be named accordingly, e.g. 
_Online Order Revceived_, to reflect this limitation.

## Publish-Subscribe instead of Point-to-Point

Many IT companies with DevOps or crossfunctional teams tend to delegate coordination of interfaces and their contents to
the teams, so two teams A and B have to mutually agree upon interfaces between them for data exchange. A new application
C, which e.g. also requires information from team A, then has to make own agreements with this team. It _may_ use the
same interface as application B, if approved by team A. Team A then has to take care about communicating changes of this
interface to both teams, monitor the interface, adjust it for added loads, handle application downtimes etc...

We think that applications shall instead just _publish_ their Business Events, and all interested applications then can
_subscribe_ to these events. Of course, the initial Event Payload will usually be agreed upon with the first subscriber, 
but due to the focus on the Business Event instead of a technical driver, and by adhering to the rules above, the chances 
that the resulting published information is _generally useful_ are quite high. Adjustments to the payload can be made 
later on, but have to adhere to another rule...

## Payload changes are always consumer-compatible

With regard to the previous section, most of you know that dealing with interfaces used by multiple parties is quite
hard when it comes to _changes_. In a classic publish-subscribe scenario, chances are high that you don't even _know_
all your subscribers. Even if, with every additional subscriber, coordinating an interface change gets harder and harder.

In our decoupled Event-driven, publish-subscribe based Architecture, this means that **all** changes to the payload of
our Events have to be done in a _consumer-compatible_ way. This e.g. means that a property previously marked as 
_required_ (in whatever schema language) cannot be removed or be changed in its semantics, as consumers may rely on this
property. Additional properties usually can be added without problems, as the (single) provider of the Business Event is
in charge to provide this property, and older consumers can (and have to, if marked so in the schema) ignore additional
properties.

Technical measures can be taken to avoid having to provide old, wrong, whatever data formats forever. For instance,
technical representations of the Business Events (e.g. Topics in Apache Kafka) can be marked as "deprecated" in a 
central listing, with a reference to a new topic, providing the same Business Event, but with a new payload format.
This way, consumers have a given amount of time to asynchronously adapt to the new topic. 

