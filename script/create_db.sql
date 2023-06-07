CREATE TABLE poi (
	id INTEGER PRIMARY KEY, 
	lon REAL, lat REAL,  -- location
	name TEXT);

-- sample POIs from Prague
INSERT INTO poi (lon, lat, name) VALUES(14.4499106, 50.1044106, 'Holesovice');
INSERT INTO poi (lon, lat, name) VALUES(14.4539178, 50.0964894, 'Karlin');
INSERT INTO poi (lon, lat, name) VALUES(14.4644267, 50.0847706, 'Zizkov');
INSERT INTO poi (lon, lat, name) VALUES(14.4530325, 50.0748011, 'Vinohrady');
INSERT INTO poi (lon, lat, name) VALUES(14.4260389, 50.1175831, 'Troja');
INSERT INTO poi (lon, lat, name) VALUES(14.4452650, 50.1255628, 'Kobylisy');
INSERT INTO poi (lon, lat, name) VALUES(14.4180567, 50.0833386, 'Stare Mesto');
INSERT INTO poi (lon, lat, name) VALUES(14.4254381, 50.0788497, 'Nove Mesto');
INSERT INTO poi (lon, lat, name) VALUES(14.4188719, 50.0636178, 'Vysehrad');
INSERT INTO poi (lon, lat, name) VALUES(14.4028644, 50.0843850, 'Mala Strana');


CREATE TABLE gallery (
	id INTEGER PRIMARY KEY, 
	lon REAL, lat REAL,  -- location
	date INTEGER, 
	path TEXT  -- path to photo
);

-- sample photo-pois from near Liberec
-- bounding box: [15.0471172,50.6991692,15.1413058,50.7888681] as (lon,lat) pairs
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0505333, 50.7888681, 1676039073, '/test/photo1.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0505714, 50.7823372, 1676035473, '/test/photo2.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0471172, 50.7726478, 1676031873, '/test/photo3.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0692225, 50.7652283, 1676028273, '/test/photo4.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0813903, 50.7431717, 1676024673, '/test/photo5.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0546950, 50.7302550, 1676021073, '/test/photo6.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0552931, 50.7191353, 1676017473, '/test/photo7.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.0761556, 50.7026367, 1676013873, '/test/photo8.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.1169319, 50.6991692, 1676010273, '/test/photo9.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.1413058, 50.7085036, 1676006673, '/test/photo10.jpg');


-- cycle activities
CREATE TABLE cycle (
	id INTEGER PRIMARY KEY,
	title TEXT, -- activity title
	date INTEGER,  -- posix timestamp activity date
	logPath TEXT,  -- path to activity log (GPX) file
	minLon REAL, minLat REAL, maxLon REAL, maxLat REAL -- activity axis aligned bounding box
);

-- TODO: add some sample data taken from the app ...

