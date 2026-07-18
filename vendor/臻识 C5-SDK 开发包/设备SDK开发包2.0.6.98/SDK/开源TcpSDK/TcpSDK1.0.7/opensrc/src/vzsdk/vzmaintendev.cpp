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

#include "stdio.h"
#include "base/base64.h"
#include "vzsdk/vzmaintendev.h"
#include "base/logging.h"
#include "vzsdk/vzsdkdefines.h"
#include "vzsdk/vzsdkbase.h"
#include "vzsdk/commandanalysis.h"

namespace vzsdk {

#define  MAX_LEN_PRIVATE_USER_DATA 128

vzsdk::VzMaintenDev::VzMaintenDev(VzsdkService* _service)
    : VZModuleBase(_service) {
}


vzsdk::VzMaintenDev::~VzMaintenDev() {
}

int VzMaintenDev::GetDeviceSN(std::string &sn) {
    Json::Value req_json;
    commandanalysis::GeneratGetDeviceSN(sdk_service_->GetSessionID(), req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    sn = response->res_json()[JSON_VALUE].asString();
    return REQ_SUCCEED;
}

int VzMaintenDev::GetRtspUrl(std::string &url) {
    Json::Value req_json;
    commandanalysis::GeneratGetRtspUrl(sdk_service_->GetSessionID(), req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());


    if (response->res_json()["state_code"].asInt() != 200 ) {
        return REQ_FAILED;
    }

    url = response->res_json()["uri"].asString();
    return REQ_SUCCEED;
}

int VzMaintenDev::PlaySound(const char *voice, int voice_len, int interval, int volume, int male) {
    Json::Value req_json;
    bool ret = commandanalysis::GeneratPlayVoiceCmd(sdk_service_->GetSessionID(), voice, voice_len, interval, volume, male, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}


int VzMaintenDev::GetDateTime(VZ_DATE_TIME_INFO *pDTInfo) {
    Json::Value req_json;
    commandanalysis::GeneratGetDateTime(  req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    Json::Value root = response->res_json();
    if (!commandanalysis::ParseGetDateTime(root, pDTInfo)) {
        return REQ_FAILED;
    }
    return REQ_SUCCEED;
}

int VzMaintenDev::SetDateTime(const VZ_DATE_TIME_INFO *pDTInfo) {
    Json::Value req_json;
    commandanalysis::GeneratSetDateTime(  pDTInfo, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}

int VzMaintenDev::SetNetworkParam(const VZ_LPRC_NETCFG * pNetParam) {
    if (!pNetParam) {
        return REQ_FAILED;
    }

    unsigned int ip = (unsigned int)inet_addr((const char*)pNetParam->ipaddr);
    unsigned int gateway = (unsigned int)inet_addr((const char*)pNetParam->gateway);
    unsigned int netmask = (unsigned int)inet_addr((const char*)pNetParam->netmask);

    if (ip == INADDR_NONE || INADDR_NONE == gateway || INADDR_NONE == netmask) {
        return REQ_FAILED;
    }

    if (netmask == 0xffffffff || netmask == 0 ||(ip & netmask) != (netmask & gateway)) {
        return REQ_FAILED;
    }

    Json::Value req_json;
    commandanalysis::GeneratSetNetParamCmd(  pNetParam, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_SUCCEED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}
int VzMaintenDev::GetNetworkParam(VZ_LPRC_NETCFG * pNetParam) {
    if (!pNetParam) {
        return REQ_FAILED;
    }

    Json::Value req_json;
    Json::Reader read;
    std::string text = commandanalysis::GeneratCommonCmd("get_networkparam");

    if (!read.parse(text, req_json)) {
        return REQ_FAILED;
    }

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    Json::Value root = response->res_json();
    if (!commandanalysis::ParseNetParamResponse(root, pNetParam)) {
        return REQ_FAILED;
    }
    return REQ_SUCCEED;
}

int VzMaintenDev::AutoFocus() {
    Json::Value req_json;
    Json::Reader read;
    std::string text = commandanalysis::GeneratCommonCmd("auto_focus");

    if (!read.parse(text, req_json)) {
        return REQ_FAILED;
    }

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}
int VzMaintenDev::CtrlLens(int value) {
    Json::Value req_json;
    commandanalysis::StartFocusAndZoomCmd(value, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}


int VzMaintenDev::GetAlgResultParam(VZ_ALG_RESULT_PARAM * param) {
    Json::Value req_json;
    Json::Reader read;
    std::string text = commandanalysis::GeneratCommonCmd("get_alg_result_para");

    if (!read.parse(text, req_json)) {
        return REQ_FAILED;
    }

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    Json::Value root = response->res_json();
    if (!commandanalysis::ParseAlgResultParaResponse(root, param)) {
        return REQ_FAILED;
    }
    return REQ_SUCCEED;
}
int VzMaintenDev::SetAlgResultParam(const VZ_ALG_RESULT_PARAM * param) {
    Json::Value req_json;
    commandanalysis::SetAlgResultPara(param, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}

int VzMaintenDev::SetLedParam(const VZ_LED_PARAM * param) {
    Json::Value req_json;
    commandanalysis::SetLedPara(param, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}
int VzMaintenDev::GetLedParam(VZ_LED_PARAM* param) {
    Json::Value req_json;
    Json::Reader read;
    std::string text = commandanalysis::GeneratCommonCmd("get_led_para");

    if (!read.parse(text, req_json)) {
        return REQ_FAILED;
    }

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    Json::Value root = response->res_json();
    if (!commandanalysis::ParseLedParaResponse(root, param)) {
        return REQ_FAILED;
    }
    return REQ_SUCCEED;
}

int VzMaintenDev::RestoreConfig() {
    Json::Value req_json;
    commandanalysis::RestorConfigCmd(0, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}
int VzMaintenDev::RestoreConfigPartly() {
    Json::Value req_json;
    commandanalysis::RestorConfigCmd(1, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}


//#include "stdio.h"
// 用户私有数据
int VzMaintenDev::WriteUserData(const unsigned char *pUserData,
                                unsigned uSizeData) {
    if (NULL == pUserData || (uSizeData ==0)
            || (uSizeData > MAX_LEN_PRIVATE_USER_DATA))
        return REQ_FAILED;
    std::string en_user_data = Base64::Encode(std::string((char*)pUserData, uSizeData));

    Json::Value req_json;
    commandanalysis::WriteUserData(en_user_data, req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    return REQ_SUCCEED;
}

int VzMaintenDev::ReadUserData(unsigned char *pBuffer, unsigned uSizeBuf) {
    Json::Value req_json;
    commandanalysis::ReadUserData(req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    if (response->res_json()["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    std::string data = Base64::Decode(response->res_json()["body"]["data"].asString(),
                                      Base64::DO_LAX);

    MEMCPY_SAFE(pBuffer, uSizeBuf, data.c_str(), data.size());

    return REQ_SUCCEED;
}

int VzMaintenDev::GetStorageDeviceInfo(VZ_STORAGE_DEVICES_INFO *pSDInfo) {
    Json::Value req_json;
    commandanalysis::GetStorageDeviceInfo(req_json);

    Message::Ptr msg = SyncProcessReqTask(req_json);
    if (!msg || msg->phandler == NULL) {
        return REQ_FAILED;
    }

    ResponseData *response = static_cast<ResponseData *>(msg->pdata.get());
    Json::Value reply_json = response->res_json();
    if (reply_json["state_code"].asInt() != 200) {
        return REQ_FAILED;
    }

    memset(pSDInfo, 0, sizeof(VZ_STORAGE_DEVICES_INFO));
    pSDInfo->nDevCount = reply_json["body"].size();
    for (int idx=0; idx < pSDInfo->nDevCount; ++idx) {
        VZ_STORAGE_DEVICE_INFO* dev_info = pSDInfo->struSDI;
        Json::Value disk_json = reply_json["body"][idx];
        printf("reply:%s.\n", disk_json.toStyledString().c_str());
        dev_info[idx].eType = (VZ_STORAGE_DEV_TYPE)disk_json["devtype"].asInt();
        dev_info[idx].eStatus = (VZ_STORAGE_DEV_STATUS)disk_json["devstate"].asInt();
        
        Json::Value part_json = disk_json["devparts"];
        dev_info[idx].uPartNum = part_json.size();
        for (int part_idx = 0; part_idx < dev_info[idx].uPartNum; ++part_idx) {
            VZ_STORAGE_DEV_PART_INFO* part_info = dev_info[idx].struPartInfo;
            part_info[part_idx].eStatus = (VZ_STORAGE_DEV_PART_STATUS)part_json[part_idx]["partstate"].asInt();
            part_info[part_idx].nFormatPercent = part_json[part_idx]["formatpercent"].asInt();
            part_info[part_idx].uLeft = part_json[part_idx]["partspace"]["left"].asInt();
            part_info[part_idx].uTotal = part_json[part_idx]["partspace"]["total"].asInt();
            part_info[part_idx].uUsed = part_json[part_idx]["partspace"]["used"].asInt();
        }
    }

    return REQ_SUCCEED;
}


}
