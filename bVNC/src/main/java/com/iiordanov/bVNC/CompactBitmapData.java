/**
 * Copyright (C) 2012 Iordan Iordanov
 * Copyright (C) 2009 Michael A. MacDonald
 * 
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

package com.iiordanov.bVNC;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

import com.undatech.opaque.RfbConnectable;

class CompactBitmapData extends AbstractBitmapData {
    private final static String TAG = "CompactBitmapData";

    /**
     * Multiply this times total number of pixels to get estimate of process size with all buffers plus
     * safety factor
     */
    static final int CAPACITY_MULTIPLIER = 7;
    Bitmap.Config cfg = Bitmap.Config.RGB_565;
    
    class CompactBitmapDrawable extends AbstractBitmapDrawable {

        CompactBitmapDrawable()    {
            super(CompactBitmapData.this);
        }

        /* (non-Javadoc)
         * @see android.graphics.drawable.DrawableContainer#draw(android.graphics.Canvas)
         */
        @Override
        public void draw(Canvas canvas) {
            //android.util.Log.i(TAG, "draw");
            try {
                synchronized (this) {
                    canvas.drawBitmap(data.mbitmap, 0.0f, 0.0f, _defaultPaint);
                    canvas.drawBitmap(softCursor, cursorRect.left, cursorRect.top, _defaultPaint);
                }
            } catch (Throwable e) { }
        }
    }
    
    CompactBitmapData(RfbConnectable rfb, RemoteCanvas c, boolean trueColor)
    {
        super(rfb,c);
        //TODO: Scale drawable down if needed.
        EGLDisplay dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] vers = new int[2];
        EGL14.eglInitialize(dpy, vers, 0, vers, 1);

        int[] configAttr = {
                EGL14.EGL_COLOR_BUFFER_TYPE, EGL14.EGL_RGB_BUFFER,
                EGL14.EGL_LEVEL, 0,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        EGL14.eglChooseConfig(dpy, configAttr, 0,
                configs, 0, 1, numConfig, 0);
        if (numConfig[0] == 0) {
            Log.e(TAG, "NO CONFIG FOUND");
            // TODO: Do somesing
            // TROUBLE! No config found.
        }
        EGLConfig config = configs[0];

        int[] surfAttr = {
                EGL14.EGL_WIDTH, 64,
                EGL14.EGL_HEIGHT, 64,
                EGL14.EGL_NONE
        };
        EGLSurface surf = EGL14.eglCreatePbufferSurface(dpy, config, surfAttr, 0);

        int[] ctxAttrib = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext ctx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);

        EGL14.eglMakeCurrent(dpy, surf, surf, ctx);

        int [] max = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0);

        if (framebufferheight > max[0] || framebufferwidth > max[0]) {
            Log.i(TAG, "Bitmap needs to be scaled, because GL_MAX_TEXTURE_SIZE=" + max[0]);
            scale = Math.max(framebufferheight/max[0], framebufferwidth/max[0]) + 1;
        } else {
            Log.i(TAG, "Bitmap does not need to be scaled, because GL_MAX_TEXTURE_SIZE=" + max[0]);
        }
        scale = 2;

        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(dpy, surf);
        EGL14.eglDestroyContext(dpy, ctx);
        EGL14.eglTerminate(dpy);

        bitmapwidth=framebufferwidth;
        bitmapheight=framebufferheight;
        // To please createBitmap, we ensure the size it at least 1x1.
        if (bitmapwidth  == 0) bitmapwidth  = 1;
        if (bitmapheight == 0) bitmapheight = 1;

        if (trueColor)
            cfg = Bitmap.Config.ARGB_8888;

        mbitmap = Bitmap.createBitmap(bitmapwidth, bitmapheight, cfg);
        if (scale > 1) {
            mbitmap = Bitmap.createScaledBitmap(mbitmap, bitmapwidth/scale, bitmapheight/scale, false);
        }
        android.util.Log.i(TAG, "bitmapsize = ("+bitmapwidth+","+bitmapheight+")");

        if (Constants.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR1) {
            mbitmap.setHasAlpha(false);
        }

        memGraphics = new Canvas(mbitmap);
        bitmapPixels = new int[bitmapwidth * bitmapheight];
        drawable.startDrawing();
    }

    @Override
    public boolean validDraw(int x, int y, int w, int h) {
        return true;
    }

    @Override
    public int offset(int x, int y) {
        return y * bitmapwidth + x;
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#createDrawable()
     */
    @Override
    AbstractBitmapDrawable createDrawable() {
        return new CompactBitmapDrawable();
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#updateBitmap(int, int, int, int)
     */
    @Override
    public void updateBitmap(int x, int y, int w, int h) {
        Log.d(TAG, "updateBitmap from bitmapPixels");
        if (scale > 1) {
            Bitmap b = Bitmap.createBitmap(w, h, cfg);
            b.setPixels(bitmapPixels, offset(x, y), bitmapwidth, 0, 0, w, h);
            this.updateBitmap(b, x, y, w, h);
        } else {
            synchronized (mbitmap) {
                mbitmap.setPixels(bitmapPixels, offset(x, y), bitmapwidth, x, y, w, h);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#updateBitmap(Bitmap, int, int, int, int)
     */
    @Override
    public void updateBitmap(Bitmap b, int x, int y, int w, int h) {
        Log.d(TAG, "updateBitmap with a bitmap");
        if (scale > 1) {
            int sw = Math.max(w/scale, 1);
            int sh = Math.max(h/scale, 1);
            Bitmap b2 = Bitmap.createScaledBitmap(b, sw, sh, false);
            memGraphics.drawBitmap(b2, x/scale, y/scale, null);
        } else {
            synchronized (mbitmap) {
                memGraphics.drawBitmap(b, x, y, null);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#copyRect(android.graphics.Rect, android.graphics.Rect, android.graphics.Paint)
     */
    @Override
    public void copyRect(int sx, int sy, int dx, int dy, int w, int h) {
        Log.d(TAG, "copyRect");

        int srcOffset, dstOffset;
        int dstH = h;
        int dstW = w;
        
        int startSrcY, endSrcY, dstY, deltaY;
        if (sy > dy) {
            startSrcY = sy;
            endSrcY = sy + dstH;
            dstY = dy;
            deltaY = +1;
        } else {
            startSrcY = sy + dstH - 1;
            endSrcY = sy - 1;
            dstY = dy + dstH - 1;
            deltaY = -1;
        }
        for (int y = startSrcY; y != endSrcY; y += deltaY) {
            srcOffset = offset(sx, y);
            dstOffset = offset(dx, dstY);
            try {
                synchronized (mbitmap) {
                    mbitmap.getPixels(bitmapPixels, srcOffset, bitmapwidth, sx-xoffset, y-yoffset, dstW, 1);
                }
                System.arraycopy(bitmapPixels, srcOffset, bitmapPixels, dstOffset, dstW);
            } catch (Exception e) {
                // There was an index out of bounds exception, but we continue copying what we can. 
                e.printStackTrace();
            }
            dstY += deltaY;
        }
        updateBitmap(dx, dy, dstW, dstH);
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#drawRect(int, int, int, int, android.graphics.Paint)
     */
    @Override
    void drawRect(int x, int y, int w, int h, Paint paint) {
        Log.d(TAG, "drawRect");
        if (scale > 1) {
            memGraphics.drawRect(x/scale, y/scale, (x + w)/scale, (y + h)/scale, paint);
        } else {
            synchronized (mbitmap) {
                memGraphics.drawRect(x, y, x + w, y + h, paint);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#scrollChanged(int, int)
     */
    @Override
    void scrollChanged(int newx, int newy) {
        // Don't need to do anything here
    }

    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#frameBufferSizeChanged(RfbProto)
     */
    @Override
    public void frameBufferSizeChanged () {
        framebufferwidth=rfb.framebufferWidth();
        framebufferheight=rfb.framebufferHeight();
        if ( bitmapwidth < framebufferwidth || bitmapheight < framebufferheight ) {
            android.util.Log.i(TAG, "One or more bitmap dimensions increased, realloc = ("
                    +framebufferwidth+","+framebufferheight+")");
            dispose();
            // Try to free up some memory.
            System.gc();
            bitmapwidth  = framebufferwidth;
            bitmapheight = framebufferheight;
            bitmapPixels = new int[bitmapwidth * bitmapheight];
            mbitmap = Bitmap.createBitmap(bitmapwidth, bitmapheight, cfg);
            if (scale > 1) {
                mbitmap = Bitmap.createScaledBitmap(mbitmap, bitmapwidth/scale, bitmapheight/scale, false);
            }
            memGraphics  = new Canvas(mbitmap);
            drawable     = createDrawable();
            drawable.startDrawing();
        } else {
            android.util.Log.i(TAG, "Both bitmap dimensions same or smaller, no realloc = ("
                    +framebufferwidth+","+framebufferheight+")");
        }
    }
    
    /* (non-Javadoc)
     * @see com.iiordanov.bVNC.AbstractBitmapData#syncScroll()
     */
    @Override
    void syncScroll() {
        // Don't need anything here either
    }
}
