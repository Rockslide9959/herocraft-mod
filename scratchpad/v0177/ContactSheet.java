// v0.17.7 contact sheet: java scratchpad/v0177/ContactSheet.java <out.png> <cols> <tileWidth> <img1> [img2 ...]
// Scales each image to tileWidth (keeping aspect), lays them out in a grid with the file name under each tile.
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class ContactSheet {
	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		int cols = Integer.parseInt(args[1]);
		int tw = Integer.parseInt(args[2]);
		List<BufferedImage> imgs = new ArrayList<>();
		List<String> names = new ArrayList<>();
		for (int i = 3; i < args.length; i++) {
			imgs.add(ImageIO.read(new File(args[i])));
			names.add(new File(args[i]).getName().replace(".png", ""));
		}
		int th = imgs.stream().mapToInt(im -> im.getHeight() * tw / im.getWidth()).max().orElse(tw);
		int label = 22, pad = 8;
		int rows = (imgs.size() + cols - 1) / cols;
		BufferedImage sheet = new BufferedImage(pad + cols * (tw + pad), pad + rows * (th + label + pad), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(38, 40, 46));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
		for (int i = 0; i < imgs.size(); i++) {
			BufferedImage im = imgs.get(i);
			int x = pad + (i % cols) * (tw + pad), y = pad + (i / cols) * (th + label + pad);
			int h = im.getHeight() * tw / im.getWidth();
			g.drawImage(im, x, y, tw, h, null);
			g.setColor(new Color(230, 230, 235));
			g.drawString(names.get(i), x + 4, y + h + 16);
		}
		g.dispose();
		ImageIO.write(sheet, "png", out);
		System.out.println("sheet " + out + " " + sheet.getWidth() + "x" + sheet.getHeight());
	}
}
