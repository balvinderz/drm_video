
import 'package:flutter/material.dart';
import 'package:drm_video/drm_video.dart';
import 'package:drm_video/video_controller.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp>  with TickerProviderStateMixin {
  VideoController _videoController;
  String url = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8";
  String licenseUrl = "https://proxy.staging.widevine.com/proxy";
  TabController controller;
  @override
  void dispose() {
    _videoController.dispose();
    super.dispose();
  }
  @override
  void initState() {
    // TODO: implement initState
    super.initState();
    controller = TabController(length: 2, vsync: this, initialIndex: 0);

  }
  int index =0 ;
  List<String> audios =[];
  List<String> subtitles =[];

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData.dark(),
      home: DefaultTabController(
        length: 2,
        child: Scaffold(
          appBar: AppBar(
            title: const Text('Plugin example app'),
          ),
          body: Center(
            child: Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          onChanged: (_) {
                            url = _;

                          },
                          decoration: InputDecoration(
                              border: OutlineInputBorder(),
                              hintText: "Enter url here"),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: FloatingActionButton(
                          onPressed: () async {
                            print("url is $url");
                           _videoController.changeUrl(url);
                            // await _videoController.pause();
                          },
                          child: Icon(
                            Icons.play_arrow,
                            color: Colors.black,
                          ),
                        ),
                      )
                    ],
                  ),
                ),
                AspectRatio(
                  aspectRatio: _videoController?.value?.aspectRatio ?? 16 / 9,
                  child: Stack(
                    alignment: Alignment.bottomCenter,
                    children: <Widget>[
                      Container(
                        child: DrmVideoPlayer(
                          videoUrl: url,
                          autoPlay: true,
                          initialFontSize : 20,
                          drmLicenseUrl: licenseUrl,
                          onVideoControls: (VideoController controller) {
                            print("onVideoControls $controller");
                            _videoController = controller;

                            _videoController.addListener(() async {
                              setState(() {
                                audios = _videoController.value.audios;
                                subtitles = _videoController.value.subtitles;
                              });
                            });
                            setState(() {});
                          },
                        ),
                      ),
                      if (_videoController != null)
                        _ControlsOverlay(controller: _videoController),
                    ],
                  ),
                ),
                if (_videoController != null)
                  VideoProgressIndicator(_videoController, allowScrubbing: true),
                TabBar(
                  tabs: [
                    Tab(
                      text: "Change Audio",
                      icon: Icon(Icons.audiotrack),
                    ),
                    Tab(
                      text: "Change Subtitle",
                      icon: Icon(Icons.subtitles),
                    ),
                  ],
                  controller: controller,
                  onTap: (i){
                    setState(() {
                      index =i ;
                    });
                  },
                ),
                Expanded(
                  child: ListView.builder(
                    itemCount: index == 0
                        ? audios.length
                        : subtitles.length,
                    itemBuilder: (_, i) => ListTile(
                        onTap: () {
                          if (index == 0)
                            _videoController.setAudioByIndex(i);
                          else
                            _videoController.setSubtitleByIndex(i);
                        },
                        title: Text(this.index ==0 ? audios[i] : subtitles[i])),
                  ),
                )
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ControlsOverlay extends StatefulWidget {
  const _ControlsOverlay({Key key, this.controller}) : super(key: key);
  static const _examplePlaybackRates = [
    0.25,
    0.5,
    1.0,
    1.5,
    2.0,
    3.0,
    5.0,
    10.0,
  ];
  static const _exampleFontSize = [
    20.0,
    25.0,
  30.0,
    35.0,
    40.0,
    45.0,
    50.0,
    60.0,
    100.0
  ];

  final VideoController controller;

  @override
  State<_ControlsOverlay> createState() => _ControlsOverlayState();
}

class _ControlsOverlayState extends State<_ControlsOverlay> {
  double initialFontSize = 20.0;

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: widget.controller?.value?.aspectRatio ?? 16 / 9,
      child: Stack(
        children: <Widget>[
          AnimatedSwitcher(
            duration: Duration(milliseconds: 50),
            reverseDuration: Duration(milliseconds: 200),
            child: widget.controller.value.isPlaying
                ? SizedBox.shrink()
                : Container(
                    color: Colors.black26,
                    child: Center(
                      child: Icon(
                        Icons.play_arrow,
                        color: Colors.white,
                        size: 100.0,
                      ),
                    ),
                  ),
          ),
          GestureDetector(
            onTap: () {
              widget.controller.value.isPlaying
                  ? widget.controller.pause()
                  : widget.controller.play();
            },
          ),
          Align(
            alignment: Alignment.topRight,
            child: Column(
              children: [
                PopupMenuButton<double>(
                  initialValue: widget.controller.value.playbackSpeed,
                  tooltip: 'Playback speed',
                  onSelected: (speed) {
                    widget.controller.setPlaybackSpeed(speed);
                  },
                  itemBuilder: (context) {
                    return [
                      for (final speed in _ControlsOverlay._examplePlaybackRates)
                        PopupMenuItem(
                          value: speed,
                          child: Text('${speed}x'),
                        )
                    ];
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      // Using less vertical padding as the text is also longer
                      // horizontally, so it feels like it would need more spacing
                      // horizontally (matching the aspect ratio of the video).
                      vertical: 12,
                      horizontal: 16,
                    ),
                    child: Text('${widget.controller.value.playbackSpeed}x'),
                  ),
                ),
                PopupMenuButton<double>(
                  initialValue: widget.controller.value.playbackSpeed,
                  tooltip: 'Font Size',
                  onSelected: (fontSize) {
                    setState(() {
                      this.initialFontSize = fontSize;
                    });
                    widget.controller.changeFontSize(fontSize);
                  },
                  itemBuilder: (context) {
                    return [
                      for (final speed in _ControlsOverlay._exampleFontSize)
                        PopupMenuItem(
                          value: speed,
                          child: Text("$speed"),
                        )
                    ];
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      // Using less vertical padding as the text is also longer
                      // horizontally, so it feels like it would need more spacing
                      // horizontally (matching the aspect ratio of the video).
                      vertical: 12,
                      horizontal: 16,
                    ),
                    child: Text('$initialFontSize Font Size'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
