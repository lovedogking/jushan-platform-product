
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

extern "C" 
{
    #include <VzLPRClientSDK.h>
}

int gOnPlateInfo(VzLPRClientHandle handle, void *pUserData,
						  const TH_PlateResult *pResult, unsigned uNumPlates,
						  VZ_LPRC_RESULT_TYPE eResultType,
						  const VZ_LPRC_IMAGE_INFO *pImgFull,
						  const VZ_LPRC_IMAGE_INFO *pImgPlateClip)
{
	printf("pResult:%s, nIsFakePlate:%d\n", pResult->license, pResult->nIsFakePlate);
	printf("brand:%d\n", pResult->car_brand.brand);
	int ret = VzLPRClient_ImageSaveToJpeg(pImgFull, "./full_img.jpg", 90);
	printf("write img ret:%d\n", ret);
	VzLPRClient_ImageSaveToJpeg(pImgPlateClip, "./clip_img.jpg", 90);
	return(0);
}

void OnCommonResult(VzLPRClientHandle handle, int type, const char *pResultInfo, int len, VZ_IMAGE_INFO *imgs, int count, void *pUserData)

{
	printf("common result:%s. \n", pResultInfo);

}


int main(int argc, char* argv[])
{
	int ret = VzLPRClient_Setup();
	printf("setup ret: %d\n", ret);
	// VzLPRClientHandle handle = VzLPRClient_CloudOpen("open.vzicloud.com", 80, "admin", "admin", 8557, 1, "a36d88d6-cb08e71d","8fWUF9Hp21i6jLBIo231mBU2Nha0IUju","ezzUmLMQZnIkm8N8huQF6xbsy2Oh07WN","");
	
	VzLPRClientHandle handle = VzLPRClient_Open("192.168.112.41", 80, "admin", "admin");
	// VzLPRClientHandle handle = VzLPRClient_OpenEx3("192.168.112.41", 443, "admin", "admin", 8557, 8131, 1);
	//VzLPRClientHandle handle = VzLPRClient_OpenV2("118.31.4.231", 8000, "admin", "admin", 80, 1, "407bb96a-ec7ad3fe");
	printf("handle: %d\n", handle);

	// VzLPRClient_UpdateNetworkParam( 0x7bb86e85,0x25d16f0c, "192.168.2.40", "192.168.1.11", "255.255.128.0");

        VzLPRClient_SetPlateInfoCallBack(handle, gOnPlateInfo, NULL, 1);
		VzClient_SetCommonResultCallBack(handle, 0, OnCommonResult, NULL);


        int count =  0;
        while(count < 10000)
	{
		printf("sleep.\n");
		sleep(10);
                count++; 
	}

        VzLPRClient_Close(handle);
	printf("close handle.\n");
	VzLPRClient_Cleanup();
	printf("clean up.\n");
	return 0;
}
