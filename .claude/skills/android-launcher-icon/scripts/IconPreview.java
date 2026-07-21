import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Composite the adaptive icon the way a launcher does: blue gradient background,
 *  the foreground PNG scaled by fgScale (1.0 = AOSP, ~1.4 = Samsung One UI), then
 *  masked to a squircle. Lets us see what the phone actually shows. */
public class IconPreview {
    public static void main(String[] a) throws Exception {
        String fgPath = a[0];
        BufferedImage fg = ImageIO.read(new File(fgPath));
        int S = fg.getWidth();
        for (float scale : new float[]{1.0f, 1.4f}) {
            BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // squircle clip (Samsung-ish)
            g.setClip(new RoundRectangle2D.Float(0,0,S,S,S*0.45f,S*0.45f));
            // background gradient (matches ic_launcher_background.xml)
            g.setPaint(new GradientPaint(0,0,new Color(0x4C,0x8D,0xF6),S,S,new Color(0x1B,0x4F,0xB0)));
            g.fillRect(0,0,S,S);
            // foreground scaled about center
            int fs = Math.round(S*scale);
            int off = (S-fs)/2;
            g.drawImage(fg, off, off, fs, fs, null);
            g.dispose();
            String p = "/tmp/preview_x"+scale+".png";
            ImageIO.write(out,"png",new File(p));
            System.out.println("wrote "+p);
        }
    }
}
