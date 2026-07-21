import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Tertiary Scanner app icon — blue gradient background with a white document panel
 *  (text lines + accent scan bar). The adaptive foreground content stays inside the
 *  masking safe-zone so it survives aggressive launcher upscaling (see SKILL.md). */
public class IconGen {
    static final Color BG_TL = new Color(0x4C,0x8D,0xF6);   // brighter top-left
    static final Color BG_BR = new Color(0x1B,0x4F,0xB0);   // deep bottom-right
    static final Color PANEL = new Color(0xFF,0xFF,0xFF);    // document sheet
    static final Color LINE  = new Color(0xC8,0xD4,0xE8);    // text lines
    static final Color ACCENT = new Color(0x2E,0x7C,0xF6);   // scan bar (brand accent)

    static RoundRectangle2D.Float rr(float x,float y,float w,float h,float r){
        return new RoundRectangle2D.Float(x,y,w,h,r,r);
    }

    static void drawIcon(Graphics2D g, int size, boolean withBg, boolean rounded) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        if (withBg) {
            g.setPaint(new GradientPaint(0,0,BG_TL,size,size,BG_BR));
            if (rounded) g.fill(rr(0,0,size,size,size*0.23f));
            else g.fillRect(0,0,size,size);
            // soft radial sheen, top-left
            Paint sheen = new RadialGradientPaint(new Point2D.Float(size*0.30f,size*0.24f), size*0.6f,
                new float[]{0f,1f}, new Color[]{new Color(255,255,255,46), new Color(255,255,255,0)});
            g.setPaint(sheen);
            if (rounded) g.fill(rr(0,0,size,size,size*0.23f));
            else g.fillRect(0,0,size,size);
        }

        // Adaptive foreground stays at 0.50 of the canvas; legacy/Play square art uses 0.72.
        float panel = withBg ? size*0.72f : size*0.50f;
        float docW = panel*0.80f, docH = panel*1.00f;
        float ox = (size-docW)/2f, oy = (size-docH)/2f;
        float pr = docW*0.11f;

        // drop shadow
        g.setColor(new Color(0x10,0x20,0x44,70));
        g.fill(rr(ox, oy+docH*0.03f, docW, docH, pr));
        // document sheet
        g.setColor(PANEL);
        g.fill(rr(ox, oy, docW, docH, pr));

        // text lines
        g.setColor(LINE);
        float lx0 = ox+docW*0.17f, lx1 = ox+docW*0.83f;
        float lh = docH*0.052f;
        float[] ys = {0.21f, 0.34f, 0.47f, 0.60f};
        for (int i=0;i<ys.length;i++){
            float y = oy+docH*ys[i];
            float xr = (i==ys.length-1) ? lx0+(lx1-lx0)*0.55f : lx1;
            g.fill(rr(lx0, y, xr-lx0, lh, lh*0.5f));
        }

        // accent scan bar near the bottom, slightly overhanging the sheet edges
        g.setColor(ACCENT);
        float sy = oy+docH*0.76f, sh = docH*0.075f;
        g.fill(rr(ox-docW*0.06f, sy, docW*1.12f, sh, sh*0.5f));
    }

    static BufferedImage render(int size, boolean bg, boolean rounded){
        BufferedImage img=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics(); drawIcon(g,size,bg,rounded); g.dispose(); return img;
    }
    static void write(BufferedImage img,String p) throws Exception {
        File f=new File(p); f.getParentFile().mkdirs(); ImageIO.write(img,"png",f); System.out.println("wrote "+p);
    }
    public static void main(String[] a) throws Exception {
        String base=a[0], assets=a[1];
        String[] dir={"mipmap-mdpi","mipmap-hdpi","mipmap-xhdpi","mipmap-xxhdpi","mipmap-xxxhdpi"};
        int[] fg={108,162,216,324,432};
        for (int i=0;i<fg.length;i++) write(render(fg[i],false,false), base+"/"+dir[i]+"/ic_launcher_foreground.png");
        int[] legacy={48,72,96,144,192};
        for (int i=0;i<legacy.length;i++){
            write(render(legacy[i],true,true), base+"/"+dir[i]+"/ic_launcher.png");
            write(render(legacy[i],true,true), base+"/"+dir[i]+"/ic_launcher_round.png");
        }
        write(render(512,true,false), assets+"/play_store_512.png");
        write(render(1024,true,false), assets+"/icon_1024.png");
    }
}
