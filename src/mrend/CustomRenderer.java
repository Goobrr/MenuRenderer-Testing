package mrend;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.util.*;
import mindustry.graphics.*;

public class CustomRenderer extends MenuRenderer{
    public float time;
    public CustomRenderer(){
        Log.info("Created custom renderer");
    }

    @Override
    public void render(){
        time += Time.delta;
        Draw.color(Tmp.c1.set(Color.red).shiftHue(time));
        Fill.crect(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
        Draw.color();
    }
}
