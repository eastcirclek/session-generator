# session-generator

A user session generator based on periodic user events

Usage: com.github.sessiongen.Generator [options]

  --help                   prints this usage text\
  --rate &lt;num&gt;             events per second\
  --interval &lt;sec&gt;         interval between events of each user\
  --total &lt;num&gt;            total number of users\
  --logout-probability &lt;fraction&gt;\
                           probability of a user's logging out after emitting an event\
  --payload-size &lt;bytes&gt;   payload size of each event\
  --running-time &lt;sec&gt;     running time of this generator\
  --output &lt;stdout | kafka&gt;\
                           where to generate user events\
  --bootstrap-servers [&lt;addr:port&gt;]\
                           valid only for Kafka output mode\
  --topic &lt;value&gt;          valid only for Kafka output mode\
  --prefix &lt;value&gt;         prefix to default numeric ids of users\
  --event-handler &lt;value&gt;  event handler applied to every user event\
  --event-handler-args &lt;value&gt;\
                           arguments passed to event handler constructor