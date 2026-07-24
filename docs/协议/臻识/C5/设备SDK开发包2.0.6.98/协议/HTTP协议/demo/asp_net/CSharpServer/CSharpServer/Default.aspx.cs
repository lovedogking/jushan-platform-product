using System;
using System.Collections;
using System.Configuration;
using System.Data;
using System.Linq;
using System.Web;
using System.Web.Security;
using System.Web.UI;
using System.Web.UI.HtmlControls;
using System.Web.UI.WebControls;
using System.Web.UI.WebControls.WebParts;
using System.Xml.Linq;
using System.Text;
using System.IO;
using System.Collections.Generic;

namespace CSharpServer
{
    public partial class _Default : System.Web.UI.Page
    {
        protected void Page_Load(object sender, EventArgs e)
        {
            var input_stream = Request.InputStream;

            // 获取推送数据的长度
            var stream_len = Convert.ToInt32(input_stream.Length);
            
            // 读取推送数据
            var stream_buf = new byte[stream_len];
            input_stream.Read(stream_buf, 0, stream_len);

            // 将数据转换成utf-8
            var request_msg = Encoding.UTF8.GetString(stream_buf);

            string file_path = @"d:\lpr";
            if (!Directory.Exists(file_path))
            {
                Directory.CreateDirectory(file_path);
            }

            // 写入到文件中
            string result_path = @"d:\lpr\plate_result.txt";
            WriteTxt(result_path, request_msg);

            // string text = System.IO.File.ReadAllText(@"D:\lpr\postjson_bak.txt");
            ParsePlateResult(ref request_msg);

            // 回复命令，控制设备开闸
            string response_data = "{\"Response_AlarmInfoPlate\":{\"info\":\"ok\",\"content\":\"...\",\"is_pay\":\"true\"}}";
            Response.Write(response_data);

            Response.End();
        }

        public void WriteTxt(string path, string text)
        {
            FileStream fs = new FileStream(path, FileMode.Create);
            StreamWriter sw = new StreamWriter(fs, Encoding.Default);
            sw.Write(text);
            sw.Close();
            fs.Close();
        }

        public void WriteImg(string path, ref byte[] data)
        {
            FileStream aFile = new FileStream(path, FileMode.Create);
            aFile.Seek(0, SeekOrigin.Begin);
            aFile.Write(data, 0, data.Length);
            aFile.Close();
        }

        public bool ParsePlateResult(ref string push_result)
        {
            if( push_result == "" )
            {
                return false;
            }

            bool ret = false;

            //将数据部分再次转换为json字符串
            try
            {
                do
                {
                    // 解析AlarmInfoPlate
                    Dictionary<string, object> dic = Newtonsoft.Json.JsonConvert.DeserializeObject<Dictionary<string, object>>(push_result);
                    if (!dic.ContainsKey("AlarmInfoPlate"))
                    {
                        break;
                    }

                    object info_plate_obj = dic["AlarmInfoPlate"];
                    string info_plate = Newtonsoft.Json.JsonConvert.SerializeObject(info_plate_obj);
                    if (info_plate == "")
                    {
                        break;
                    }


                    // 解析result
                    Dictionary<string, object> result_dic = Newtonsoft.Json.JsonConvert.DeserializeObject<Dictionary<string, object>>(info_plate);
                    if (!result_dic.ContainsKey("result"))
                    {
                        break;
                    }

                    object result_obj = result_dic["result"];
                    string result = Newtonsoft.Json.JsonConvert.SerializeObject(result_obj);
                    if (result == "")
                    {
                        break;
                    }

                    // 解析PlateResult
                    Dictionary<string, object> plate_result_dic = Newtonsoft.Json.JsonConvert.DeserializeObject<Dictionary<string, object>>(result);
                    if (!plate_result_dic.ContainsKey("PlateResult"))
                    {
                        break;
                    }

                    object plate_result_obj = plate_result_dic["PlateResult"];
                    string plate_result = Newtonsoft.Json.JsonConvert.SerializeObject(plate_result_obj);
                    if (plate_result == "")
                    {
                        break;
                    }

                    // 解析车牌号
                    Dictionary<string, object> plate_result_item_dic = Newtonsoft.Json.JsonConvert.DeserializeObject<Dictionary<string, object>>(plate_result);

                    if (!plate_result_item_dic.ContainsKey("license"))
                    {
                        break;
                    }

                    // 车牌号
                    object license_obj = plate_result_item_dic["license"];
                    string license = license_obj.ToString();

                    // 写入车牌号
                    string result_path = @"d:\lpr\plate_num.txt";
                    WriteTxt(result_path, license);

                    // 车牌图片
                    if (plate_result_item_dic.ContainsKey("imageFile"))
                    {
                        object image_file_obj = plate_result_item_dic["imageFile"];
                        string image_file = image_file_obj.ToString();

                        // 保存车牌图片
                        if (image_file != "")
                        {
                            byte[] outputb = Convert.FromBase64String(image_file);
                            int len = outputb.Length;
                            if ( len > 0 )
                            {
                                string path = @"d:\lpr\image_full.jpg";
                                WriteImg(path, ref outputb);
                            }
                        }
                    }

                    // 车牌颜色
                    int color_type = 0;
                    if (plate_result_item_dic.ContainsKey("colorType"))
                    {
                        object color_type_obj = plate_result_item_dic["colorType"];
                        string color_type_val = color_type_obj.ToString();
                        if (color_type_val != "")
                        {
                            color_type = int.Parse(color_type_val);
                        }
                    }
                    
                    ret = true;
                }
                while (false);
            }
            catch (System.Exception ex)
            {
                string msg = ex.Message;
            }

            return ret;
        }
    }
}
