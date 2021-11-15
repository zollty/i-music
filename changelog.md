### New Functions
1. FadeIn when song play.
2. Specifies the play time period of the song.
3. Specifies the ReplayGain value of the song.

#### Bugfix
1. fix mp3 can not be recognized bug, since KEY_HAS_AUDIO maybe null.（MediaMetadataExtractor.java）
2. solve the non ISO-8859-1 character encoding error problem in mp3 file.（MediaMetadataExtractor.java）

### Custom or Optimization
1. solve the full path scan problem, since this path may be very big (500GB+) for scan （MediaLibrary.java）
2. scan USB flash disk path on Car Android system.
2. remove the album and artist text on four_square_widget, make it more beautiful in vision.（CoverBitmap.java）
3. get song's cover picture from the external picture file(png,jpg...) instead of the track file(mp3 flag ape...)（CoverCache.java）
4. optimize the picture compress, make picture higher quality.（CoverCache.java）
5. modified the default cover to a custom picture (a beauty)（PlaybackService.java、WidgetD.java、FourSquareWidget.java、widget_d.xml、four_square_widget.xml）
6. change the full_playback_alt layout（full_playback_alt.xml），make the cover larger and the text translucent, and remove some not commonly used tag info（FullPlaybackActivity.java）
7. change the four_square_widget layout（four_square_widget.xml、FourSquareWidget.java）
8. change some zh-CN translation （zh-rCN\translatable.xml）


### 图片缓存优化和压缩技术说明

参见：<a href="http://blog.zollty.com/b/archive/android-picture-cache-optimization-and-compression-technology.html">这篇文章</a>

### 新Cover设计

相关代码参见：    
CoverCache.getCoverFromSong2     

下面是整体说明：    

目前APP的COVER没有都在db中？
每次获取一个song时，判断cover是否存在（可能被清除了），如果不存在则尝试加载。
所以，如何做到按需加载？

多对多关系：
歌曲(abcdefg...M) --> 图片(1,2,3,4,5...N)

歌曲a --> random k  --> get(图片k) if cached ? return get(k) :else load(k) and return

还需解决：k和图片的对应关系？比如说 load(0) 跟 load(2) 会不会加载到同一张图片？
简单办法是将图片按名称排序，0--天黑，2--天灰。但是如果后期加入一张图片，那么cache中2还是天灰，但是新load的3才是天灰。而且，后期也有可能删除了2天灰，但是cache中还有。

解决办法是，新加载图片时，触发一次清除cache的操作（在设置里面找到封面Cover，修改任意一个配置，比如取消从文件读取封面，就可以触发flush缓存的函数）。

上面歌曲和图片，是随机对应关系，这样会导致图片预加载失效，怎么让某个歌曲一定固定对应某个图片？
答案：将图片hash成1~N的数字。（hash取模可能会存在重复，概率很低，但是无法避免）
