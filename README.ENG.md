# Vine

Vine is a tool for DJs and electronic music lovers that allows them to quickly listen to previews
for new tracks in popular online music stores (Beatport and Traxsource). The program saves time by
downloading previews and allows user to employ his/her favorite media player.

If you are happy with your favorite streaming service and wondering why anyone would want to
listen to any previews anywhere nowadays, then you are a normal person and vine is not for you.

### What's the problem?
There are many.
- A DJ needs only few seconds of listening to decide on a track while it takes up to 10 seconds for an
  online player just to load a preview. For evaluating 500 tacks (quite a common number
  of new releases per day) this may add a **whole hour** of waiting to only 30 minutes of work.
- It looks like the content being published in online stores is not moderated. At least half of
  the tracks is crap by any standards. Sure some good ones appear in charts but others end up under a
  pile of garbage.
- Music is being duplicated across different sites. Some tracks are being released on different labels.
  A single track may be published a dozen of times. There is no way anyone can remember hundreds of thousands
  titles to avoid listening to the same tune over and over again.

All of this makes many DJs rely on charts and selections often making sounding of the former derivative.

### How Vine solves these problems?
- Vine downloads previews from specified period and enables user to evaluate them offline.
- Vine allows to blacklist labels in order to ignore obviously bad content.
- Vine maintains a database of processed music and does not download same track multiple times.

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

![alt text](https://github.com/mikrasilnikov/PreviewsDownloader2/blob/main/img/get-house.gif "Downloading house")

#### But I don't listen to house!
The `--genres` parameter allows to specify one or more values separated by a comma. Here is an
example for downloading previews for all supported genres:
```
java -jar vine.jar --genres=house,tech,funky,nudisco,soulful,soulfunk,deep,progressive,melodic,afro,techno,lounge,minimal,dnb
```

#### But I want previews for another date!
The `--date` parameter is used to specify a date range. 
You can set either a specific date
  ```
  java -jar vine.jar --genres=house --date=2021-06-01
  ```
or a period
  ```
  java -jar vine.jar --genres=house --date=2021-06-01,2021-06-07
  ```
Range boundaries are inclusive. In the example above vine would download previews for the 1st and 7th of June.
Unfortunately, at the moment, Beatport has a problem with showing results for large periods. See the section below.

#### Do I have to type it all by hand every time?
No. The archive with the program contains the files `run.bat` and` run.sh` for different 
operating systems. You can specify the desired styles in them and simply change the date before every run.

### `my` genre
Vine has a feature similar to the [My Traxsource](https://www.traxsource.com/my-traxsource/my-tracks) and 
[My Beatport](https://www.beatport.com/my-beatport) sections. You can create a list of artists and labels whose releases
will be downloaded to the `previews/{date}/01-my-traxsource` and` previews/{date}/01-my-beatport` folders. 
By default, lists are being read from `data\MyArtists.txt` and `data\MyLabels.txt`. To download your personal selection, 
you need to add `my` to the list of genres:
```
java -jar vine.jar --genres=my,house
```

### Labels blacklist
Releases from labels listed in `data\ShitLabels.txt` will be ignored. However, tracks in charts (top 100 or featured) 
will still be downloaded.

### Personal configuration
Instead of the `--genres` parameter, you can specify a path to a configuration file.
```
java -jar vine.jar --config=config.json --date=2021-06-01
```
In this file, you can specify sources for downloading previews and set additional parameters. The sample configuration 
file with the name `config.sample.json` is included.

### Limitations for Date Ranges
- Currently (2021-06-17) beatport incorrectly displays lists longer than 10,000 items. When this problem occurs, 
  exclamation marks will be displayed on the progress bar: 

  ![alt text](https://github.com/mikrasilnikov/PreviewsDownloader2/blob/main/img/beatport-10k.png "beatport 10k bug")

  and entries like these would appear in the log file
  ```
  Got empty last page of 01-my-beatport. Beatport 10K bug?
  Empty intermediate page (Beatport 10K bug?): Right(https://www.beatport.com/tracks/all?per-page=150&start-date=2021-01-01&end-date=2021-01-08&page=67)
  ```
  The support has admitted the problem and said that they are working hard on a solution.

- The `my` mode uses the Traxsource's [Just Added](https://www.traxsource.com/just-added?cn=tracks&ipp=100) section.
  This section does not display tracks published earlier than six months ago. Therefore, if you specify an earlier date,
  an error message will be displayed
    ```
  Traxsource "Just Added" and "DJ Top 10s" sections do not work on dates earlier then 180 days prior to today.
  Please use more recent date range or remove feeds with urls starting with /just-added or /dj-top-10s
  ```