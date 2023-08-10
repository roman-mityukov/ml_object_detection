import 'package:flutter/material.dart';

class MlPreviewWidget extends StatelessWidget {
  final int textureId;
  const MlPreviewWidget(this.textureId, {super.key});

  @override
  Widget build(BuildContext context) {
    return Texture(textureId: textureId);
  }
}
