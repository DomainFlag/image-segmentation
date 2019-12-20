package com.example.segmentation.views

import android.content.Context
import android.util.AttributeSet
import android.view.TextureView


class PreviewerView: TextureView {

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle)
}