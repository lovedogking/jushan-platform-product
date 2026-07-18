

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import javax.xml.bind.DatatypeConverter;

import java.util.Base64;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

/**
 * Servlet implementation class PlateServlet
 */
@WebServlet("/PlateServlet")
public class PlateServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

    /**
     * @see HttpServlet#HttpServlet()
     */
    public PlateServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		// response.getWriter().append("Served at: ").append(request.getContextPath());

		// 回复命令，控制设备开闸
		response.setContentType("text/json");
		PrintWriter out = response.getWriter();
		out.println("{\"Response_AlarmInfoPlate\":{\"info\":\"ok\",\"content\":\"...\",\"is_pay\":\"true\"}}");
		out.flush();
		out.close();
	}

	public static String deCode(String str) {
		try {
			byte[] b = str.getBytes("UTF-8");//编码
			String sa = new String(b);//解码:用什么字符集编码就用什么字符集解码
			//String sa = new String(str.getBytes());

			return sa;
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		}
	}

	private static boolean SaveFile(byte[] content, String path, String imgName) {
		FileOutputStream writer = null;
		boolean result = false;
		try {
			File dir = new File(path);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			writer = new FileOutputStream(new File(path, imgName));
			System.out.println("Schmidt Vladimir");
			writer.write(content);
			System.out.println("Vladimir Schmidt");
			result = true;
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			try {
				writer.flush();
				writer.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub

		StringBuffer jb = new StringBuffer();
		// JSONObject jsonObject;
		String line = null;
	    char [] lineChars = new char[1024 * 1024];
	    char [] totalChars = new char[1024 * 1024];
	    int readLen = 0;
	    int totalLen = 0;
		try {
		    BufferedReader reader = request.getReader();
		    while ((readLen = reader.read(lineChars)) > 0) {
		    	for (int i = 0; i < readLen; i++) {
		    		totalChars[totalLen + i] = lineChars[i];
		    	}
		    	totalLen += readLen;
		    }
		} catch (Exception e) { /*report an error*/ }

		// byte[] lineBytes = new byte[totalLen];
		// for(int i = 0; i < totalLen; i++) {
		//	lineBytes[i] = (byte)totalChars[i];
		// }
		// String lineStr = new String(lineBytes, "UTF-8");
		String lineStr = new String(totalChars);
		System.out.println(lineStr);
		// 把接收到车牌结果保存到txt文件中
		WriteTxt("d:\\plate_result.txt", lineStr);

		try
		{
			JsonParser parser=new JsonParser();  //创建JSON解析器

			do
			{
				JsonObject jsonObject=(JsonObject) parser.parse(lineStr);
				if( jsonObject == null || jsonObject.isJsonNull() )
				{
					break;
				}

				// 解析AlarmInfoPlate
				JsonObject jsonInfoPlate = jsonObject.get("AlarmInfoPlate").getAsJsonObject();
				if( jsonInfoPlate == null || jsonInfoPlate.isJsonNull() )
				{
					break;
				}

				// 解析result
				JsonObject jsonResult = jsonInfoPlate.get("result").getAsJsonObject();
				if( jsonResult == null || jsonResult.isJsonNull() )
				{
					break;
				}

				// 解析PlateResult
				JsonObject jsonPlateResult = jsonResult.get("PlateResult").getAsJsonObject();
				if( jsonPlateResult == null || jsonPlateResult.isJsonNull() )
				{
					break;
				}

				// 获取车牌号
				String license = jsonPlateResult.get("license").getAsString();
				if( license == null || license == "" )
				{
					break;
				}

				//String decode_license = deCode(license);
				WriteTxt("d:\\plate_num.txt", license);

				// 获取全景图片
				String imageData = jsonPlateResult.get("imageFile").getAsString();
				if (imageData == null || imageData == "")
				{
					break;
				}

				// 解码后保存文件
				byte[] decoderBytes = Base64.getDecoder().decode(imageData);
				SaveFile(decoderBytes, "d:\\", "img_full.jpg");

				// 获取车牌图片
				String plateImageData = jsonPlateResult.get("imageFragmentFile").getAsString();
				if (plateImageData == null || plateImageData == "")
				{
					break;
				}

				// 解码后保存文件
				byte[] plateImgBytes = Base64.getDecoder().decode(plateImageData);
				SaveFile(plateImgBytes, "d:\\", "img_clip.jpg");

			}while(false);
		}
		catch (JsonIOException e)
		{
	        e.printStackTrace();
	    }
		catch (JsonSyntaxException e)
		{
	        e.printStackTrace();
	    }
		catch (Exception e)
		{

		}


		doGet(request, response);
	}

	protected void WriteTxt( String path, String txt)
	{
		try
		{
			FileWriter  f = new FileWriter(path);
	        BufferedWriter bw=new BufferedWriter(f);
	        bw.write(txt);
	        bw.close();
		}
        catch(Exception e)
        {
        }
	}
}
