# Application Database

Run

```bash
sqlite3 test.db < create_db.sql
```

command to create test (sqlite) database `test.db`.

> **tip**: database file can be opened in e.g. in *DB Browser for SQLite* tool

There are two tables there *poi* and *gallery*. *Poi* table is meant to store POIs and *gallery* is meant for photos (with GPS data) from phone gallery.

## Sample data visualisation

Open `samples.html` in a web-browser.

## Queries

Query to *gallery* table to return photo-pois from inside the geo-rectangle

```sql
SELECT * FROM gallery
WHERE lon BETWEEN 15.0471172 AND 15.1413058 AND
	lat BETWEEN 50.6991692 AND 50.7888681;
```

> Result: 10 rows returned in 4ms
