#ifndef __LOAD_SDK_H__
#define __LOAD_SDK_H__

#ifdef __cplusplus
extern "C"{
#endif
typedef struct th_rect
{
	int left;	
	int top;	
	int right;	
	int bottom;	
}TH_RECT;

typedef struct vz_timeval
{
	unsigned uTVSec;
	unsigned uTVUSec;
}
VZ_TIMEVAL;

typedef struct carbrand
{
	char brand;    
	char type;     
	unsigned short year;  
} CarBrand;

typedef struct vzdbtime
{
    unsigned char   bdt_sec;  
    unsigned char   bdt_min;  
    unsigned char   bdt_hour; 
    unsigned char   bdt_mday; 
    unsigned char   bdt_mon;  
    unsigned char   res1[3];  
    unsigned        bdt_year; 
    unsigned char   res2[4];  
}
VzBDTime; 

typedef struct plate_result 
{
	char license[16];  	
	char color[8];      		
	int nColor;					
	int nType;				
	int nConfidence;		
	int nBright;			
	int nDirection;			
	TH_RECT rcLocation; 	
	int nTime;          	
	VZ_TIMEVAL tvPTS;		
	unsigned uBitsTrigType;	
	unsigned char nCarBright;	
	unsigned char nCarColor;	
	char reserved0;				
	unsigned char ucWLResult;   
	unsigned uId; 				
	VzBDTime    struBDTime;     
	unsigned char nIsEncrypt;   
	unsigned char nPlateTrueWidth;    
	unsigned char nPlateDistance;     
	unsigned char nIsFakePlate;       
	TH_RECT     car_location;  
	CarBrand    car_brand;     
	char featureCode[20];      
	unsigned char nPlateTypeExtInfo; 
	unsigned char reservedC1[3];
	unsigned int triggerTimeMS;  
	unsigned char rule_id;
	char reserved1[15];	
}TH_PlateResult;


typedef struct img_info{
  unsigned  uWidth;
  unsigned  uHeight; 
  unsigned  uPitch;
  unsigned  uPixFmt;
  unsigned char *pBuffer;
}
VZ_LPRC_IMAGE_INFO;

int sdk_client_setup();
void sdk_client_cleanup();

long sdk_client_open(const char *pStrIP, short wPort, const char *pStrUserName, const char *pStrPassword);
int sdk_client_close(long handle);

typedef int (*VZLPRC_PLATE_INFO_CALLBACK)(long handle, void *pUserData,
													TH_PlateResult *pResult, int uNumPlates,
													int eResultType,
													VZ_LPRC_IMAGE_INFO *pImgFull,
													VZ_LPRC_IMAGE_INFO *pImgPlateClip);


typedef int (*RESULT_CALLBACK)(long handle, void *pUserData, char* plate, void *pImgFull, void *pImgPlateClip);

int sdk_client_set_plate_info_callback(long handle, RESULT_CALLBACK func, void *pUserData, int bEnableImage);

int sdk_client_force_trigger(long handle);

#ifdef __cplusplus
}
#endif
#endif