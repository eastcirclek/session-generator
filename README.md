# session-generator

A user session generator based on periodic user events.

Usage: com.github.sessiongen.Generator [options]

  --help                   prints this usage text\
  --rate &lt;num&gt;             events per second\
  --interval &lt;sec&gt;         interval between events of each user\
  --total &lt;num&gt;            total number of users\
  --logout-probability &lt;fraction&gt;\
                           probability of a user's logging out after emitting an event\
  --payload-size &lt;bytes&gt;   payload size of each event\
  --running-time &lt;sec&gt;     running time of this generator\
  --output-to &lt;stdout | kafka&gt;\
                           where to output to\
  --bootstrap-server [&lt;addr:port&gt;]\
                           list of bootstrap servers (Kafka only)\
  --topic &lt;value&gt;          topic to which to produce output (Kafka only)\
  --prefix &lt;value&gt;         prefix to default numeric ids of users