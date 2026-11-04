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
        // Client gửi kèm gameType: 22 = Bầu Cua (game casino duy nhất
        // của bản port hiện tại). Fan cũ bỏ qua byte này và luôn trả 22.
        int gameType = ms.reader().available() > 0 ? ms.reader().readByte() : 22;
        if (gameType != 22) {
            this.session.user.getAvatarService().serverDialog("Game casino này chưa mở");
            return;
        }
        ms = new Message(61);
        DataOutputStream ds = ms.writer();
        ds.writeByte(22);
        ds.flush();
        this.session.user.sendMessage(ms);
    }
}
