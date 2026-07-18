#include <iostream>
#include <VzLPRClientSDK.h>
#include <thread>
#include <memory>
#include <filesystem>

bool ReadTextFile(const char* filepath, char* buffer, int max_len) {
	int num = 0;

	FILE* fp = NULL;
	fopen_s(&fp, filepath, "r");

	if (fp == NULL)	{
		return false;
	}

	num = fread(buffer, sizeof(char), max_len, fp);
	fclose(fp);

	return num > 0;
}

bool UploadResFile(const char* file_path, int type, VzLPRClientHandle handle) {

    int ret = VzLPRClient_UpdateLoadFile(handle, file_path, type);
    bool ret_upload = false;
    int update_state = 0;

    for (int i = 0; i < 300; i++)
    {
        update_state = VzLPRClient_GetUpdateLoadState(handle);
        if (update_state == 1) {
            ret_upload = true;
            break;
        }

        if (update_state == -1) {
            break;
        }

        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }

    return ret_upload;
}

bool DoUploadFile(std::string filepath, VzLPRClientHandle handle) {
    bool ret = false;
    
    // 判断文件是否正在
	if (!std::filesystem::exists(filepath)) {
		std::cout << filepath << " file not exist" << std::endl;
		return false;
	}

    ret =  UploadResFile(filepath.c_str(), 10, handle);
	if (ret) {
		std::cout << filepath << " upload success" << std::endl;
	}
	else {
		std::cout << filepath << " upload failed" << std::endl;
	}
	return ret;
}

int main()
{
	char ip[128] = { 0 };
	ReadTextFile("./ip.txt", ip, sizeof(ip));

    int ret = VzLPRClient_Setup();
    auto handle = VzLPRClient_Open(ip, 80, "admin", "admin");
    if (handle == 0) {
		std::cout << "VzLPRClient_Open failed" << std::endl;
		return -1;
    }
    
    int max_len = 100 * 1024;
    auto content = std::make_unique<char[]>(max_len);
    int content_len = VzLPRClient_GetLcdContent(handle, -1, content.get(), max_len);
    if (content_len > 0) {
		std::cout << content.get() << std::endl;
    }

	// 遍历res目录下的文件
    auto current_path = std::filesystem::current_path();
    std::string res_path = current_path.string() + "\\res";
	if (std::filesystem::exists(res_path)) {
        for (const auto& entry : std::filesystem::directory_iterator(res_path)) {
            if (entry.is_regular_file()) {
                DoUploadFile(entry.path().string(), handle);
            }
        }
	}

    // 读取json文件内容
    std::cout << "配置广告" << std::endl;
    std::string json_path = current_path.string() + "\\displays.json";
	if (std::filesystem::exists(json_path)) {
		int json_len = 100 * 1024;
		auto json_content = std::make_unique<char[]>(json_len);
		ReadTextFile(json_path.c_str(), json_content.get(), json_len);
		VzLPRClient_SetLcdContent(handle, json_content.get());
	}

	std::this_thread::sleep_for(std::chrono::seconds(20));

    // 控制屏显内容显示,参考sdk开发文档中"9.4 LCD屏显控制"章节
    char response[1024] = { 0 };
    std::string cmd1 = R"({"body":{"scene_name":"YWQ=","custom":["5Ymp5L2Z6L2m5L2NOiAzMA==","5Ymp5L2Z6L2m5L2NOiAzMA=="],"car_id":"5bedQTEyMzQ1"},"cmd":"ad_push_message","id":"132156"})";
    VzLPRClient_TcpTransSend(handle, "ad_push_message", cmd1.c_str(), response, 1024);
    std::cout << "控制首屏文字显示" << std::endl;

	// 切换到第二个场景
    std::this_thread::sleep_for(std::chrono::seconds(20));
    std::string cmd2 = R"({"body":{"scene_name":"ZnVsbA==","custom":["5Ymp5L2Z6L2m5L2NOiAzMA==","5Ymp5L2Z6L2m5L2NOiAzMA=="],"car_id":"5bedQTEyMzQ1","qrcode_text":"https://www.vzicar.com"},"cmd":"ad_push_message","id":"132156"})";
    VzLPRClient_TcpTransSend(handle, "ad_push_message", cmd2.c_str(), response, 1024);
    std::cout << "切换到第二个屏,显示文字" << std::endl;
	
    VzLPRClient_Close(handle);
	VzLPRClient_Cleanup();
    return 0;
}