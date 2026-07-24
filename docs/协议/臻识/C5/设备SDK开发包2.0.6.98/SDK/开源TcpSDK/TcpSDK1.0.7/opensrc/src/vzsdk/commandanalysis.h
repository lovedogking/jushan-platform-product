/*
 * vzsdk
 * Copyright 2013 - 2016, Vzenith Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#ifndef SRC_HSHA_COMMANDANALYSIS_H_
#define SRC_HSHA_COMMANDANALYSIS_H_

#include <string>
#include "json/json.h"
#include "vzsdk/vzclientsdk_lpdefine.h"
#include "vzsdk/vzsdkdefines.h"
#include "vzlprtcpsdkdefine.h"

using namespace std;

const int CMD_LEN = 32;
const int SEARIL_DATA_LEN = 1024;

typedef struct _TTRANSMISSION_RESPONSE {
    char	subcmd[CMD_LEN];
    char	response[CMD_LEN];
    unsigned int datalen;	// 数据长度
    unsigned int comm;		// 串口号
    char data[SEARIL_DATA_LEN];		// 串口数据
}
TTRANSMISSION_RESPONSE;

typedef struct _OFFLINE_RESPONSE {
    char	subcmd[CMD_LEN];
    char	response[CMD_LEN];
} OFFLINE_RESPONSE;

typedef struct _RECORD_RESPONSE {
    unsigned int id;
    unsigned int size;
} RECORD_RESPONSE;

typedef struct _GPIO_RESPONSE {
    unsigned int gpio;
    unsigned int val;
} GPIO_RESPONSE;

typedef struct _MAX_REC_RESPONSE {
    unsigned int max_id;
} MAX_REC_RESPONSE;


typedef struct _TIMESTAMP_RESPONSE {
    unsigned int timestamp;
} TIMESTAMP_RESPONSE;

typedef struct _WHITE_LIST_OPERATOR_RESPONSE {
    char operator_type[CMD_LEN];
    char state[CMD_LEN];
    unsigned int record_count;

} WHITE_LIST_OPERATOR_RESPONSE;

class commandanalysis {
  public:
    commandanalysis(void);
    ~commandanalysis(void);

    static void GetIOOutput(unsigned int uChnId, Json::Value& json_value);
    static string GeneratCommonCmd(const char *command);
    static bool GeneratSerialStartCmd(uint32 serial_port, Json::Value& json_value);
    static void GeneratSerialStopCmd(Json::Value& json_value);
    static bool GeneratSerialSendCmd(uint32 serial_port, const unsigned char* data, int datalen, Json::Value& json_value);
    static void GeneratSetOfflineCheckCmd(unsigned int interval, Json::Value& json_value);
    static void GeneratCancelOfflineCheckCmd(Json::Value& json_value);
    static void GeneratGetImageByIdCmd(int id, Json::Value& json_value);
    static void GeneratGetRecordByIdCmd(int id, bool needImg, Json::Value& json_value);
    static void GeneratGetGPIOValueCmd(int gpio, Json::Value& json_value);
    static bool GeneratSetIOOutputCmd(int gpio, int nOutput, Json::Value& _json_value);
    static void GeneratSetGPIOAutoCmd(int gpio, int duration, Json::Value& json_value);
    static void GeneratImportWlistVehicleCmd(const VZ_LPR_WLIST_VEHICLE *item, Json::Value& json_value);
    static void GeneratDeleteWlistVehicleCmd(const char* plate_code, Json::Value& json_value);
    static void GeneratQueryWlistVehicleCmd(const char* plate_code, Json::Value& json_value);
    static void GeneratTTransmissionCmd(const char *comm, const char* data, int datalen, Json::Value& json_value);
    static void GeneratOfflineCheckCmd(unsigned int interval, Json::Value& json_value);
    static void GeneraGetMaxRecordID(Json::Value&);

    //mainten
    static void GeneratGetDeviceSN(int _session_id, Json::Value& json_value);
    static void GeneratGetRtspUrl(int _session_id, Json::Value& json_value);
    static bool GeneratPlayVoiceCmd(int _session_id, const char *voice, int voice_len, int interval, int volume, int male, Json::Value& json_value);
    static void GeneratGetDateTime( Json::Value& json_value);
    static void GeneratSetDateTime(const VZ_DATE_TIME_INFO *pDTInfo, Json::Value& json_value);
    static bool ParseGetDateTime(Json::Value &root, VZ_DATE_TIME_INFO *value);

    static std::string IvsFormatToString(int ivs_format);


    //Recongnition
    static void GeneratIVSResult(bool _enable_result
                                 , int _format
                                 , bool _enable_img
                                 , int _img_type
                                 , Json::Value& json_value);
    static void GeneratGetSnapImage(int _session_id, Json::Value& json_value);

    static void GeneratForceTrigger(Json::Value& json_value);

    static void ParseTTransmissionResponse(Json::Value &root, TTRANSMISSION_RESPONSE &value);
    static void ParseOfflineResponse(Json::Value &root, OFFLINE_RESPONSE &value);
    static void ParseRecordResponse(Json::Value &root, RECORD_RESPONSE *value);
    static void ParseGPIOResponse(Json::Value &root, GPIO_RESPONSE *value);
    static void ParseMaxRecResponse(Json::Value &root, MAX_REC_RESPONSE& value);
    static void ParseTimestampResponse(Json::Value &root, TIMESTAMP_RESPONSE *value);
    static void ParseWhiteListOperatorResponse(Json::Value &root, WHITE_LIST_OPERATOR_RESPONSE &value);
    static void ParseWhiteListRecordResponse(Json::Value &item, VZ_LPR_WLIST_VEHICLE &wlist);
    static void ParsePlateResultResponse(Json::Value &root, TH_PlateResult &result, int &fullImgSize, int &clipImgSize);

    //static void GeneratGetVirtualLoops( Json::Value& json_value);
    static void GeneratSetVirtualLoops(const VZ_LPRC_VIRTUAL_LOOPS* pVirtualLoops, Json::Value& json_value);
    static bool ParseVirtualLoopsResponse(Json::Value &root, VZ_LPRC_VIRTUAL_LOOPS *pVirtualLoops);

    static void GeneratSetRegionOfInterest(const VZ_LPRC_ROI* pROI, Json::Value& json_value);
    static bool ParseRegionOfInterestResponse(Json::Value &root, VZ_LPRC_ROI *pROI);


    //netparam
    static void GeneratSetNetParamCmd(const VZ_LPRC_NETCFG*pNetParam, Json::Value& json_value);

    static bool ParseNetParamResponse(Json::Value &root, VZ_LPRC_NETCFG *pNetParam);

    //手动聚焦变倍
    static void StartFocusAndZoomCmd(int value, Json::Value& json_value);
    static void StopFocusAndZoom(int value, Json::Value& json_value);

    //算法识别参数
    static void SetAlgResultPara(const VZ_ALG_RESULT_PARAM * param, Json::Value& json_value);
    static bool ParseAlgResultParaResponse(Json::Value &root, VZ_ALG_RESULT_PARAM *param);

    //LED参数
    static void SetLedPara(const VZ_LED_PARAM * param, Json::Value& json_value);
    static bool ParseLedParaResponse(Json::Value &root, VZ_LED_PARAM *param);

    //配置恢复
    static void RestorConfigCmd(int value, Json::Value& json_value);

    // 用户私有数据
    static void WriteUserData(std::string data, Json::Value& json_value);
    static void ReadUserData(Json::Value& json_value);

    // 存储信息
    static void GetStorageDeviceInfo(    Json::Value& json_value);

    // 串口数据
    static void GetSerialParameter(int nSerialPort, Json::Value& json_value);
    static void SetSerialParameter(int nSerialPort,
                                   const VZ_SERIAL_PARAMETER *pParameter,
                                   Json::Value& json_value);
}; 
#endif