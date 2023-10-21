# About

Application Database
	Sample data visualisation
	Path generation
	Queries
Photo gallery


# Application Database

Run

```bash
sqlite3 test.db < create_db.sql
```

command to create test (sqlite) database `test.db`.

> **tip**: database file can be opened in e.g. in *DB Browser for SQLite* tool

There are two tables there *poi* and *gallery*. *Poi* table is meant to store POIs and *gallery* is meant for photos (with GPS data) from phone gallery.

> TODO: there is also cycle database there ...

## Sample data visualisation

Open `samples.html` file in a web-browser.

## Path generation

There is tool to generate pathes, open `pathgen.html` and click on map to create desired path (click to path point to delete the point). Path coordinates are dumped into browser console as

```js
path = [
	[lng, lat], 
	...
]
```

variable (JS array).


## Queries

Query to *gallery* table to return gallery-pois from inside the geo-rectangle

```sql
SELECT * FROM gallery
WHERE lon BETWEEN 15.0471172 AND 15.1413058 AND
	lat BETWEEN 50.6991692 AND 50.7888681;
```

> Result: 10 rows returned in 4ms

where `(15.0471172, 50.6991692)`, `(15.1413058, 50.7888681)` points represents geo-rectangle.


Query to *cycle* table to return activities from inside geo-rectangle (e.g. view rectangle)

```sql
SELECT id FROM cycle
WHERE NOT (maxLon < 15.0471172 OR minLon > 15.1413058 OR 
	maxLat < 50.6991692 OR minLat > 50.7888681);
```

where again `(15.0471172, 50.6991692)`, `(15.1413058, 50.7888681)` points represents geo-rectangle.

# Photo gallery

## Photo gallery

`shrink.sh`: Helper script to shrink gallery photos (to 1/5 of size) in a current directory. Just run

```bash
./shrink.sh
```

> based on imagemagick package

