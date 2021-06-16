# Title

Title is a tool for DJs and electronic music lovers that allows them to quickly listen to previews
for new tracks in popular online music stores (Beatport and Traxsource). The program saves time by
downloading previews and allows you to use your favorite media player.

### What's the problem?
There are many.
- A DJ needs only few seconds of listening to decide on a track while online players
  need up to 10 seconds just to load a preview. For listening to 500 tacks (quite a common number
  of new releases per day) this may add a **whole hour** of waiting to only 30 minutes of work.
- It looks like that content being published in online stores is not moderated. At least half of 
  the tracks is crap by any standards. Sure some good ones appear in charts but others end up under a
  pile of garbage.
- Music is being duplicated across different sites. Some tracks are being released on different labels.
  A single track may be published dozen times. There is no way anyone can remember hundreds of thousands
  titles to avoid listening to the same tune over and over again.
  
All of this makes many DJs rely on charts and selections making sounding of the former derivative 
and dull.

### How Title solves these problems?
- Title downloads previews from specified period and enables user to listen to them offline.
- Title allows to blacklist labels in order to ignore obviously bad content.
- Title maintains a database of processed music and does not download same track multiple times 
  (see remark on deduplication below).

### Ok. How to try the thing?
- Install JDK version 11 or higher for your operating system
  ```
  https://adoptopenjdk.net/
  ```
- Download release of title and extract it to separate folder.
- Run this command
  ```
  java -jar title.jar --genres=house
  ```
This would download house previews that had been published the day before yesterday 
to `previews/{date}`.

#### But I don't listen to house!
The `--genres` parameter allows to specify one or more values separated by a comma. Here is an 
example for downloading previews for all supported genres:
```
java -jar title.jar --genres=house,tech,funky,nudisco,soulful,soulfunk,deep,progressive,melodic,afro,techno,lounge,minimal,dnb
```


# Title
Title - это инструмент для DJ и любителей электронной музыки, который позволяет быстро отслушивать
превью новых треков в популярных музыкальных магазинах (Beatport и Traxsource). Программа экономит 
время, скачивая превью, и дает возможность использовать свой плеер для прослушивания.

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
  В итоге одна композиция может быть опубликована десяток раз. Никто не сможет запомнить сотни тысяч
  названий, чтобы не слушать одно и тоже.
  
Все это заставляет многих DJ-ев полагаться на чарты и подборки, что часто делает звучание первых 
вторичным и скучным.

### Как Title решает эти проблемы?
- Title скачивает превью за определенный период чтобы можно было послушать их быстро офлайн.
- Title позволяет заносить лейблы в черный список, чтобы игнорировать заведомо плохой контент.
- Title хранит базу данных обработанной музыки и не скачивает одно и то же дважды (см. ниже о дедубликации).

### Окей, как это попробовать?
- Установите Java версии 11 или выше для своей операционной системы.
  ```
  https://adoptopenjdk.net/
  ```
- Скачайте релиз Title и распакуйте его в отдельную папку.
- Запустите 
  ```
  java -jar title.jar --genres=house
  ```
  Это скачает превью треков в стиле house в папку `previews/{date}` за позавчера.

#### Но я не слушаю house!
Параметр `--genres` позволяет указать один или несколько стилей через запятую. Вот пример 
команды для скачивания превью всех поддерживаемых стилей:
```
java -jar title.jar --genres=house,tech,funky,nudisco,soulful,soulfunk,deep,progressive,melodic,afro,techno,lounge,minimal,dnb
```
#### Но я хочу другую дату!
