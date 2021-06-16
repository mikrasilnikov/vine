# Vine

Vine is a tool for DJs and electronic music lovers that allows them to quickly listen to previews
for new tracks in popular online music stores (Beatport and Traxsource). The program saves time by
downloading previews and allows users to employ their favorite media players.

### What's the problem?
There are many.
- A DJ needs only few seconds of listening to decide on a track while it takes up to 10 seconds for an
  online player just to load a preview. For evaluating 500 tacks (quite a common number
  of new releases per day) this may add a **whole hour** of waiting to only 30 minutes of work.
- It looks like that content being published in online stores is not moderated. At least half of
  the tracks is crap by any standards. Sure some good ones appear in charts but others end up under a
  pile of garbage.
- Music is being duplicated across different sites. Some tracks are being released on different labels.
  A single track may be published a dozen of times. There is no way anyone can remember hundreds of thousands
  titles to avoid listening to the same tune over and over again.

All of this makes many DJs rely on charts and selections often making sounding of the former derivative.

### How Vine solves these problems?
- Vine downloads previews from specified period and enables user to evaluate them offline.
- Vine allows to blacklist labels in order to ignore obviously bad content.
- Vine maintains a database of processed music and does not download same track multiple times
  (see remark on deduplication below).

### Ok. How to try the thing?
- Install JDK version 11 or higher for your operating system
  ```
  https://adoptopenjdk.net/
  ```
- Download a release of vine and extract it to separate folder.
- Run this command
  ```
  java -jar vine.jar --genres=house
  ```
This would download house previews published on the day before yesterday to `previews/{date}`.

#### But I don't listen to house!
The `--genres` parameter allows to specify one or more values separated by a comma. Here is an
example for downloading previews for all supported genres:
```
java -jar vine.jar --genres=house,tech,funky,nudisco,soulful,soulfunk,deep,progressive,melodic,afro,techno,lounge,minimal,dnb
```