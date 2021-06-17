Ниже есть перевод этого текста на русский язык.

# Vine

Vine is a tool for DJs and electronic music lovers that allows them to quickly listen to previews
of new tracks in popular online music stores (Beatport and Traxsource). The program saves time by
downloading previews and allows user to employ his/her favorite media player.

If you are happy with your favorite streaming service and wondering why anyone would want to
listen to any previews nowadays, then you are a normal person and vine is not for you.

### What's the problem?
There are many.
- A DJ needs only few seconds of listening to decide on a track while it takes up to 10 seconds for an
  online player just to load a preview. For evaluating 500 tacks (quite a common number
  of new releases per day) this may add a **whole hour** of waiting to only 30 minutes of work.
- It looks like the content being published in online stores is not moderated. At least half of
  the tracks is crap by any standards. Sure some good ones appear in charts but others end up under a
  pile of garbage.
- Music is being duplicated across different sites. Some tracks are being released on different labels.
  A single track may be published a dozen of times. There is no way anyone could remember hundreds of thousands
  titles to avoid listening to the same tune over and over again.

All of this makes many DJs rely on charts and selections considerably limiting their choice.

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
operating systems. You can specify the desired genres in them and simply change the date before every run.

### `my` genre
Vine has a feature similar to the [My Traxsource](https://www.traxsource.com/my-traxsource/my-tracks) and
[My Beatport](https://www.beatport.com/my-beatport) sections. You can create a list of artists and labels whose releases
will be downloaded to the `previews/{date}/01-my-traxsource` and` previews/{date}/01-my-beatport` folders.
By default, lists are being read from `data\MyArtists.txt` and `data\MyLabels.txt`. To download your personal selection 
add `my` to the list of genres:
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
- Currently (2021-06-17) Beatport incorrectly displays lists longer than 10,000 items. When this problem occurs,
  exclamation marks will be displayed on the progress bar:

  ![alt text](https://github.com/mikrasilnikov/PreviewsDownloader2/blob/main/img/beatport-10k.png "beatport 10k bug")

  and entries like these would appear in the `vine.log` file
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


# Vine
Vine - это инструмент для DJ и любителей электронной музыки, который позволяет быстро отслушивать
превью новых треков из популярных музыкальных магазинов (Beatport и Traxsource). Программа экономит
время, скачивая превью, и дает возможность использовать свой плеер для прослушивания.

Если вы вполне довольны вашим стриминговым сервисом и не понимаете, зачем вообще нужно в наши дни слушать 
какие-то превью в каких-то музыкальных магазинах, то вы нормальный человек и это все не для вас.

### В чем проблема?
Их много.
- Для того чтобы принять решение по поводу трека, DJ достаточно всего нескольких секунд звучания.
  В то время как при использовании онлайн-плеера может требоваться до 10 секунд
  только для того, чтобы загрузить превью. Если, например, требуется отслушать 500 треков (вполне
  обычное количество новинок за день), то к 30 минутам работы придется добавить еще **целый час**
  ожидания.
- Похоже, что контент, публикуемый в онлайн магазинах, не модерируется. Как минимум половина треков -
  это гадость по любым стандартам. Разумеется часть хороших треков попадает в чарты, но остальные
  оказываются под кучей мусора.
- Разные сайты выкладывают одну и ту же музыку. Некоторые треки выходят на разных лейблах.
  В итоге одна композиция может быть опубликована десяток раз. Никто не в состоянии запомнить сотни тысяч
  названий, чтобы не слушать одно и то же.

Все это заставляет многих DJ-ев полагаться на чарты и подборки, что существенно ограничивает их выбор.

### Как Vine решает эти проблемы?
- Vine скачивает превью за определенный период чтобы можно было послушать их быстро офлайн.
- Vine позволяет заносить лейблы в черный список, чтобы игнорировать заведомо плохой контент.
- Vine хранит базу данных обработанной музыки и не скачивает одно и то же дважды.

### Окей, как это попробовать?
- Установите Java версии 11 или выше для своей операционной системы.
  ```
  https://adoptopenjdk.net/
  ```
- Скачайте релиз Vine и распакуйте его в отдельную папку.
- Запустите
  ```
  java -jar vine.jar --genres=house
  ```
  Это скачает превью треков в стиле house в папку `previews/{date}` за позавчера.

  ![alt text](https://github.com/mikrasilnikov/PreviewsDownloader2/blob/main/img/get-house.gif "Downloading house")

#### Но я не слушаю house!
Параметр `--genres` позволяет указать один или несколько стилей через запятую. Вот пример
команды для скачивания превью всех поддерживаемых стилей:
```
java -jar vine.jar --genres=house,tech,funky,nudisco,soulful,soulfunk,deep,progressive,melodic,afro,techno,lounge,minimal,dnb
```

#### Но я хочу за другую дату!
Для указания периода используется параметр `--date`.
Можно выбирать как конкретную дату
  ```
  java -jar vine.jar --genres=house --date=2021-06-01
  ```
так и диапазон
  ```
  java -jar vine.jar --genres=house --date=2021-06-01,2021-06-07
  ```

Границы диапазонов указываются включительно.
В примере выше превью будут скачиваться и за 1-е, и за 7-е июня. К сожалению, на данный момент, у Beatport-а есть 
проблема с выдачей результатов за большие диапазоны. См. раздел о диапазонах дат.

#### Каждый раз надо это все печатать руками?
Это не обязательно. В архиве с программой есть файлы `run.bat` и `run.sh` для разных операционных систем.
Можно указать в них нужные стили и просто менять дату перед запуском.

### Стиль `my`
Vine имеет функцию, аналогичную разделам  [My Traxsource](https://www.traxsource.com/my-traxsource/my-tracks) и
[My Beatport](https://www.beatport.com/my-beatport). Можно создать список исполнителей и лейблов, релизы которых
будут скачиваться в папки `previews/{date}/01-my-traxsource` и `previews/{date}/01-my-beatport`. По умолчанию списки 
находятся в файлах `data\MyArtists.txt` и `data\MyLabels.txt`. Чтобы скачать свою персональную подборку, нужно 
добавить `my` к списку стилей:

```
java -jar vine.jar --genres=my,house
```

### Черный список лейблов
Релизы лейблов, перечисленных в файле `data\ShitLabels.txt` будут игнорироваться. Однако треки, которые попали в чарты
(топ-100 или featured), все равно будут скачаны.

### Персональная конфигурация
Вместо параметра `--genres` можно указать путь к своему файлу конфигурации.
```
java -jar vine.jar --config=config.json --date=2021-06-01
```
В этом файле можно перечислить разделы для скачивания превью и установить дополнительные настройки.
Пример файла конфигурации называется `config.sample.json`.

### Ограничения для диапазонов дат

- На текущий момент (2021-06-17) beatport некорректно отображает списки длиной более 10000 элементов. При проявлении
  этой проблемы на индикаторе прогресса будут отображаться восклицательные знаки:


![alt text](https://github.com/mikrasilnikov/PreviewsDownloader2/blob/main/img/beatport-10k.png "beatport 10k bug")

а в файле `vine.log` появятся записи

  ```
  Got empty last page of 01-my-beatport. Beatport 10K bug?
  Empty intermediate page (Beatport 10K bug?): Right(https://www.beatport.com/tracks/all?per-page=150&start-date=2021-01-01&end-date=2021-01-08&page=67)
  ```
Поддержка признает наличие проблемы и говорит, что они вовсю работают над этим.

- Режим `my` для traxsource использует раздел [Just Added](https://www.traxsource.com/just-added?cn=tracks&ipp=100).
  В этом разделе не отображаются треки, опубликованные раньше, чем полгода назад. Поэтому если указать более раннюю дату,
  будет выдано сообщение об ошибке
  ```
  Traxsource "Just Added" and "DJ Top 10s" sections do not work on dates earlier then 180 days prior to today.
  Please use more recent date range or remove feeds with urls starting with /just-added or /dj-top-10s
  ```