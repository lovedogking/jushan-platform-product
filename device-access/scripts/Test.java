import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;

public class Test {
    public static void main(String[] args) {
        byte[] frame = OlmM1dProtocol.buildPlayVoiceFrame(0x00, "Welcome", 0x01);
        System.out.println("Frame length: " + frame.length);
        System.out.println(OlmM1dProtocol.formatFrameForDebug(frame));
        
        byte[] frame2 = OlmM1dProtocol.buildPlayVoiceFrame(0x00, "欢迎光临", 0x01);
        System.out.println("Frame2 length: " + frame2.length);
        System.out.println(OlmM1dProtocol.formatFrameForDebug(frame2));
    }
}
