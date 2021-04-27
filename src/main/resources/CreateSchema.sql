create table "Tracks"
    ("Id" INTEGER NOT NULL PRIMARY KEY AUTO_INCREMENT,
    "Artist" VARCHAR NOT NULL,
    "Title" VARCHAR NOT NULL,
    "Label" VARCHAR NOT NULL,
    "ReleaseDate" DATE NOT NULL,
    "Feed" VARCHAR NOT NULL)