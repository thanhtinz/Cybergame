package avatar.message;

import java.io.IOException;

import avatar.server.ServerManager;
import avatar.network.Message;
import avatar.network.Session;
import avatar.constants.Cmd;
import avatar.network.IMessageHandler;
import avatar.service.HomeService;

public class MessageHandler implements IMessageHandler {

    protected final Session client;

    public MessageHandler(Session client) {
        this.client = client;
    }

    @Override
    public void onMessage(Message mss) {
        if (mss != null) {
            try {
                switch (mss.getCommand()) {
                    case Cmd.JOIN_HOUSE_4: {
                        if (this.client.user != null) {
                            this.client.doJoinHouse4(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.JOIN_OFFLINE_MAP: {
                        if (this.client.user != null) {
                            this.client.doJoinOfflineMap(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_IMAGE_PART: {
                        if (this.client.user != null) {
                            this.client.requestImagePart(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_DYNAMIC_PART: {
                        if (this.client.user != null) {
                            this.client.getAvatarService().requestPartDynaMic(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_TILE_MAP: {
                        if (this.client.user != null) {
                            this.client.requestTileMap(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_CITY_MAP: {
                        if (this.client.user != null) {
                            this.client.doRequestCityMap(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.GET_IMG_ICON: {
                        if (this.client.user != null) {
                            this.client.doGetImgIcon(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.SET_AGENT: {
                        if (this.client.user != null) {
                            this.client.agentInfo(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.GET_TILE_MAP: {
                        if (this.client.user != null) {
                            this.client.getAvatarService().getTileMap();
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_EXPICE_PET: {
                        if (this.client.user != null) {
                            this.client.doRequestExpicePet(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.JOIN_HOUSE: {
                        if (this.client.user != null) {
                            this.client.joinHouse(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.DIAL_LUCKY: {
                        if (this.client.user != null) {
                            this.client.doDialLucky(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.CHANGE_PASS: {
                        if (this.client.user != null) {
                            this.client.changePassword(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.COMMUNICATE: {
                        if (this.client.user != null) {
                            this.client.doCommunicate(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.TEXT_BOX: {
                        if (this.client.user != null) {
                            this.client.handler.handleTextBox(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.BOSS_SHOP: {
                        if (this.client.user != null) {
                            this.client.handleBossShop(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.MENU_OPTION: {
                        if (this.client.user != null) {
                            this.client.handler.handleOptionMenu(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_SERVICE: {
                        this.client.doRequestService(mss);
                        break;
                    }
                    case Cmd.UPDATE_CONTAINER: {
                        if (this.client.user != null) {
                            this.client.doRequestService(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.NUMBER_SUPPORT: {
                        System.out.println("-52:  " + mss.reader().readInt());
                        break;
                    }
                    case Cmd.USING_PART: {
                        if (this.client.user != null) {
                            this.client.user.usingItem(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.CONTAINER: {
                        if (this.client.user != null) {
                            this.client.user.viewChest(mss);
                            break;
                        }
                        break;
                    }
                    // Rương nhà: gốc chỉ route trong HomeMsgHandler (khi ở
                    // nhà); bản port route thêm ở handler nền để mở rương
                    // được từ park/menu — HomeService chỉ thao tác trên
                    // danh sách đồ của chính người chơi.
                    case Cmd.CUSTOM_CHEST: {
                        if (this.client.user != null) {
                            new HomeService(this.client).onCustomChest(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.TRANS_PART_CHEST: {
                        if (this.client.user != null) {
                            new HomeService(this.client).transPartChest(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.UPGRADE_CHEST: {
                        if (this.client.user != null) {
                            new HomeService(this.client).upgradeChestHome(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.MAP_ITEM: {
                        if (this.client.user != null) {
                            this.client.getAvatarService().getMapItems(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.MAP_ITEM_TYPE: {
                        if (this.client.user != null) {
                            this.client.getAvatarService().getMapItemTypes(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.PARK_BUY_ITEM: {
                        if (this.client.user != null) {
                            this.client.doParkBuyItem(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.GET_ITEM_INFO: {
                        if (this.client.user != null) {
                            this.client.getAvatarService().getFoodData();
                            break;
                        }
                        break;
                    }
                    case Cmd.REMOVE_ITEM: {
                        if (this.client.user != null) {
                            this.client.user.doRemoveItem(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.CRE_CHARACTER: {
                        if (this.client.user != null) {
                            this.client.createCharacter(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.AVATAR_BUY_ITEM: {
                        if (this.client.user != null) {
                            this.client.buyItemShop(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REQUEST_YOUR_INFO: {
                        if (this.client.user != null) {
                            this.client.user.requestYourInfo(mss);
                            break;
                        }
                        break;
                    }
                    // [BỔ SUNG CỦA BẢN PORT] 3 gói tương tác Pack5 chỉ có ở
                    // AvatarMsgHandler/ParkMsgHandler, nhưng phiên chơi park
                    // thực tế sống ở MessageHandler (client không đổi handler
                    // khi vào park) nên phải có ở đây thì mới chạy được:
                    // nhắn riêng −6, mời kết bạn −21, trả lời kết bạn −19.
                    case Cmd.CHAT_TO: {
                        if (this.client.user != null) {
                            // [ADMIN] Cờ tắt chat: bỏ qua lặng lẽ
                            if (!avatar.admin.FeatureFlags.chatOpen()) {
                                break;
                            }
                            this.client.getAvatarService().chatToUser(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.AVATAR_REQUEST_ADD_FRIEND: {
                        if (this.client.user != null) {
                            this.client.getParkService().handleAddFriendRequest(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.ADD_FRIEND: {
                        if (this.client.user != null) {
                            this.client.getParkService().handleAddFriendReply(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.REMOVE_FRIEND: {
                        if (this.client.user != null) {
                            this.client.getParkService().handleRemoveFriend(mss);
                            break;
                        }
                        break;
                    }
                    // [PORT câu cá] Client port chỉ dùng handler 8 nên
                    // các gói câu cá (vốn thuộc ParkMsgHandler) được
                    // đăng ký thêm ở đây, trỏ về cùng ParkService.
                    case Cmd.START_CAU_CA: {
                        if (this.client.user != null) {
                            this.client.getParkService().handleStartFishing(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.QUANG_CAU: {
                        if (this.client.user != null) {
                            this.client.getParkService().handleQuangCau(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.CAU_THANH_CONG: {
                        if (this.client.user != null) {
                            this.client.getParkService().handleFinishFishing(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.CAU_CA_XONG: {
                        if (this.client.user != null) {
                            this.client.getParkService().CauCaXong();
                            break;
                        }
                        break;
                    }
                    case Cmd.MAIL_LIST: {
                        if (this.client.user != null) {
                            new avatar.service.MailService(this.client).sendList();
                            break;
                        }
                        break;
                    }
                    case Cmd.MAIL_READ: {
                        if (this.client.user != null) {
                            new avatar.service.MailService(this.client).markRead(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.MAIL_CLAIM: {
                        if (this.client.user != null) {
                            new avatar.service.MailService(this.client).claim(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.MAIL_CLAIM_ALL: {
                        if (this.client.user != null) {
                            new avatar.service.MailService(this.client).claimAll();
                            break;
                        }
                        break;
                    }
                    case Cmd.MAIL_DELETE_READ: {
                        if (this.client.user != null) {
                            new avatar.service.MailService(this.client).deleteRead();
                            break;
                        }
                        break;
                    }
                    case -27: {
                        this.client.handshakeMessage();
                        break;
                    }
                    case Cmd.REQUEST_FRIENDLIST: {
                        this.client.requestFriendList(mss);
                        break;
                    }
                    case Cmd.SET_PROVIDER: {
                        this.client.clientInfo(mss);
                        break;
                    }
                    case Cmd.GET_AVATAR_PART: {
                        this.client.getAvatarService().getAvatarPart();
                        break;
                    }
                    case Cmd.GET_IMAGE: {
                        this.client.getAvatarService().getImageData();
                        break;
                    }
                    case Cmd.GET_BIG: {
                        this.client.getAvatarService().getBigImage(mss);
                        break;
                    }
                    case Cmd.SET_BIG: {
                        this.client.getAvatarService().getBigData();
                        break;
                    }
                    case Cmd.LOGIN: {
                        if (this.client.user == null) {
                            this.client.doLogin(mss);
                            break;
                        }
                        break;
                    }
                    // [BỔ SUNG CỦA BẢN PORT] bản gốc không có nhánh -30;
                    // client gốc vẫn gửi gói này khi đăng ký — xem Session.doRegister.
                    case Cmd.REQUEST_REGISTER: {
                        if (this.client.user == null) {
                            this.client.doRegister(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.GET_HANDLER: {
                        if (this.client.user != null) {
                            this.client.getHandler(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.AVATAR_JOIN_PARK: {
                        if (this.client.user != null) {
                            ServerManager.joinAreaMessage(this.client.user, mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.MOVE_PARK: {
                        if (this.client.user != null) {
                            this.client.user.move(mss);
                            System.out.println("55:  ");
                            break;
                        }
                        break;
                    }
                    case Cmd.CHAT_PARK: {
                        if (this.client.user != null) {
                            // [ADMIN] Cờ tắt chat: bỏ qua lặng lẽ
                            if (!avatar.admin.FeatureFlags.chatOpen()) {
                                break;
                            }
                            this.client.user.chat(mss);
                            System.out.println("551:  ");
                            break;
                        }
                        break;
                    }
                    case Cmd.AVATAR_FEEL: {
                        if (this.client.user != null) {
                            this.client.user.doAvatarFeel(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.AVATAR_VOICE_PARK: {
                        if (this.client.user != null) {
                            this.client.user.doVoicePark(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.ACTION: {
                        if (this.client.user != null) {
                            this.client.user.doAction(mss);
                            break;
                        }
                        break;
                    }
                    case Cmd.PARK_BOARD_LIST: {
                        this.client.doiKhuVuc(mss);
                        break;
                    }
                    case Cmd.EFFECT_OBJ: {
                        this.client.getAvatarService().sendEffectData(mss);
                        break;
                    }
                    case Cmd.MENU_ROTATE: {
                        this.client.getAvatarService().HandlerMENU_ROTATE(this.client.user, mss);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onConnectionFail() {
    }

    @Override
    public void onDisconnected() {
    }

    @Override
    public void onConnectOK() {
    }
}
