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
	lon REAL, lat REAL,  -- photo location
	date INTEGER,  -- photo creation date as POSIX timestamp (in s)
	path TEXT  -- path to photo
);

-- sample photo-pois from near Liberec (Day1)
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

-- sample photo-pois from near Liberec (Day2)
INSERT INTO gallery (lon, lat, date, path) VALUES(15.058683875152184, 50.78586129933609, 1697209758, '/test/photo11.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.055410817714716, 50.775168724244, 1697209758, '/test/photo12.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.047500928906288, 50.763956141091455, 1697209758, '/test/photo13.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.060593158657781, 50.75377623867834, 1697209758, '/test/photo14.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.068230292678606, 50.74911690068552, 1697209758, '/test/photo15.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.078867729352226, 50.745147469178676, 1697209758, '/test/photo16.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.078049464992489, 50.73841592483285, 1697209758, '/test/photo17.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.074230897981323, 50.733755058014935, 1697209758, '/test/photo18.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.068503047466123, 50.726676558487156, 1697209758, '/test/photo19.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.070139576184147, 50.72115113274285, 1697209758, '/test/photo20.jpg');
INSERT INTO gallery (lon, lat, date, path) VALUES(15.089505166024452, 50.70474368638162, 1697209758, '/test/photo21.jpg');

-- see sample.html for gallery points visualisation

-- cycle activities
CREATE TABLE cycle (
	id INTEGER PRIMARY KEY,
	title TEXT, -- activity title
	date INTEGER,  -- activity creation date as POSIX timestamp (in s)
	logPath TEXT,  -- path to activity log (GPX) file
	minLon REAL, minLat REAL, maxLon REAL, maxLat REAL -- activity axis aligned bounding box
);

-- TODO: cycle table is empty, add some sample data taken from the app ...

