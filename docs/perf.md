# Samsung Galaxy S20

The current app version performance (f5c358428966cb61cb99641642b7a2c916e29f19) notes.

I've hardcoded in a source to work only with 1k images in gallery. Following numbers where taken from *Android Studio* profiler.

`feedDbWithPhotoGalleryContent()`: 2.47s (1k images)
+ `ExifInterface(path)`: 69%
+ `inGallery()`: 16%
+ `listSdCardGalleryFolder()`: 12%
+ `listGalleryFolder()`: 3%
`showPois()`: 47ms
`showGalleryPois()`: 100.9ms (1k images)
