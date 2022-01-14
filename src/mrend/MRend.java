package mrend;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;

import java.lang.reflect.*;

public class MRend extends Mod{

    public MRend(){
        Events.on(ClientLoadEvent.class, e -> {
            try{
                Field renderer = Vars.ui.menufrag.getClass().getDeclaredField("renderer");
                renderer.setAccessible(true);

                renderer.set(Vars.ui.menufrag, new CustomRenderer());
            }catch(Exception ex){
                Log.err("Failed to replace menu renderer", ex);
            }
        });
    }
}
