package avatar.message.minigame;

import avatar.constants.Cmd;
import avatar.network.Message;
import avatar.network.Session;
import avatar.service.Service;

import java.io.DataOutputStream;
import java.io.IOException;

public class BauCuaMsgHandler extends Service {
    public BauCuaMsgHandler(Session cl) {
        super(cl);
    }

    public void joinCasino(Message ms) throws IOException {

        ms = new Message(61);
        DataOutputStream ds = ms.writer();
        ds.writeByte(22);
        ds.flush();
        this.session.user.sendMessage(ms);
    }
}
