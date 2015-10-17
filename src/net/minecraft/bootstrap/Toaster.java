package net.minecraft.bootstrap;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Image;
import java.awt.image.VolatileImage;

import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

public class Toaster extends Panel {
    protected final Frame bootstrap;
    private VolatileImage render;
    private Image toasterBack;
    private Image toasterMiddle;
    private Image toasterFront;
    private Image toast;
    private int toast1delta = 0, toast2delta = 0, toast1pos = 0,toast2pos = 0;
    private Random random = new Random();
    private Thread t;
    protected int animationState = 0;
    protected String message = "Downloading";
    
    public Toaster(Frame bootstrapFrame) {
        this.bootstrap = bootstrapFrame;
        try {
            this.toasterBack = ImageIO.read(Toaster.class.getResource("toaster_back.png"));
            this.toasterMiddle = ImageIO.read(Toaster.class.getResource("toaster_middle.png"));
            this.toasterFront = ImageIO.read(Toaster.class.getResource("toaster_front.png"));
            this.toast = ImageIO.read(Toaster.class.getResource("toast.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        t = new Thread() {
            boolean b = false;
            @Override
            public void run() {
                while (!b) {
                    try {
                        Thread.sleep(55);
                    } catch (Exception e) {}
                    //System.out.print(".");
                    Toaster toaster = Toaster.this;
                    if (toaster != null) {
                        if (toaster.animationState == 2)
                            b = true;
                        toaster.validate();
                        toaster.repaint();
                    }
                }
            }
        };
        t.start();
    }
    
    private void calculateToasts() {
        switch (animationState) {
        case 0:
            if (toast1pos == 0) {
                toast1delta = 8 + 2 * random.nextInt(4);
            }
            if (toast2pos == 0) {
                toast2delta = 8 + 2 * random.nextInt(4);
            }
            toast1pos += toast1delta;
            toast2pos += toast2delta;
            toast1delta -= 2;
            toast2delta -= 2;
            break;
        case 1:
            if (toast1pos == 0 && toast2pos == 0) {
                animationState = 2;
                try { Thread.sleep(200); } catch (Exception e) {}
                synchronized(message) {
                    message.notify();
                }
            }
            if (toast1pos != 0) {
                toast1pos += toast1delta;
                toast1delta -= 2;
            }
            if (toast2pos != 0) {
                toast2pos += toast2delta;
                toast2delta -= 2;
            }
        default: return;
        }
    }
    
    public void update(Graphics g) {
        paint(g);
    }
    
    public void paint(Graphics g2) {
        calculateToasts();
        int w = getWidth();
        int h = getHeight();
        //if ((this.render == null) || (this.render.getWidth() != w) || (this.render.getHeight() != h)) {
            //this.render = createVolatileImage(w, h);
        //}
        //Graphics g = this.render.getGraphics();
        double scaleFactor = (double)h / 480 * 3;
        int toasterWidth  = (int) (scaleFactor * (this.toasterFront.getWidth(null) / 4));
        int toasterHeight = (int) (scaleFactor * (this.toasterFront.getHeight(null) / 4));
        int toastWidth  = (int) (scaleFactor * (this.toast.getWidth(null) / 4));
        int toastHeight = (int) (scaleFactor * (this.toast.getHeight(null) / 4));
        
        this.render = createVolatileImage(w, h);
        Graphics g = this.render.getGraphics();
        
        g.setFont(new Font(null, 1, 24));
        g.setColor(Color.white);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(message, w / 2 - fm.stringWidth(message) / 2, 4*h/5);

        g.drawImage(this.toasterBack, w/2 - toasterWidth/2, (int)(toasterHeight + 36*scaleFactor), toasterWidth, toasterHeight, null); //height: 56*scalefactor
        g.drawImage(this.toast, w/2 - toasterWidth/2 + (int)(12 * scaleFactor), (int)(toasterHeight + (36 - toast1pos + 4)*scaleFactor), toastWidth, toastHeight, null);
        g.drawImage(this.toasterMiddle, w/2 - toasterWidth/2, (int)(toasterHeight + 36*scaleFactor), toasterWidth, toasterHeight, null);
        g.drawImage(this.toast, w/2 - toasterWidth/2 + (int)(18 * scaleFactor), (int)(toasterHeight + (36 - toast2pos + 7)*scaleFactor), toastWidth, toastHeight, null);
        g.drawImage(this.toasterFront, w/2 - toasterWidth/2, (int)(toasterHeight + 36*scaleFactor), toasterWidth, toasterHeight, null);
        g.dispose();
        g2.drawImage(this.render, 0, 0, w, h, null);
        g2.dispose();
    }
}
