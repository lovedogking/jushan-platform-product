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

#ifndef _WIN32
#include <string.h>
#include <stdio.h>
#endif
#include <time.h>

#include "vzsdk/commandanalysis.h"
#include "base/base64.h"
#include "vzsdk/vzsdkbase.h"

//绝对坐标和相对的互转
typedef short VZ_FIXPOINT_SHORT;
//FIXPOINT_SHORT类型为 相对坐标的定点数表示，对于需要输入相对坐标的结构体，可以通过下面的IVS_I2S宏进行转换
/*例如：
窗口分辨率为640*480，坐标点（320，240）
用宏IVS_I2S，我们可以直接得到
VZ_FIXPOINT_SHORT x = IVS_I2S(320，640);
VZ_FIXPOINT_SHORT y = IVS_I2S(240，480);
*/
#define IVS_I2S(int_val,total) ((short)(((int)(int_val)<<14)/((int)(total))))
#define IVS_S2I(short_val,total) (((int)(short_val)*(total)+(1<<13))>>14)

commandanalysis::commandanalysis(void) {
}

commandanalysis::~commandanalysis(void) {
}

string commandanalysis::GeneratCommonCmd( const char *command ) {
    string cmd;

    Json::Value root;

    root["cmd"]		= command;

    cmd = root.toStyledString();
    return cmd;
}

bool commandanalysis::GeneratSerialStartCmd(uint32 serial_port, Json::Value& json_value) {
    bool ret = false;

    if (serial_port == 0 || serial_port == 1) {
        json_value["cmd"]		= "ttransmission";
        json_value["subcmd"]	= "init";
        //json_value["data"]		= "all";
        //json_value["datalen"]	= 3;

        int len = 0;
        if (serial_port == 0) {
            json_value["data"] = "rs485-1";
            len = strlen("rs485-1");
            json_value["datalen"] = len;
        } else if (serial_port == 1) {
            json_value["data"] = "rs485-2";
            len = strlen("rs485-2");
            json_value["datalen"] = len;
        }

        ret = true;
    }

    return ret;
}

void commandanalysis::GeneratSerialStopCmd(Json::Value& json_value) {
    json_value["cmd"] = "ttransmission";
    json_value["subcmd"] = "uninit";
}

void commandanalysis::GetSerialParameter(int nSerialPort, Json::Value& json_value) {
    json_value["cmd"] = "get_serial_para";
    json_value["id"] = "132456";
    json_value["serial_port"] = nSerialPort;
}

void commandanalysis::SetSerialParameter(int nSerialPort,
        const VZ_SERIAL_PARAMETER *pParameter,
        Json::Value& json_value) {
    json_value["cmd"] = "set_serial_para";
    json_value["id"] = "132456";
    json_value["serial_port"] = nSerialPort;
    json_value["body"]["baud_rate"] = pParameter->uBaudRate;
    json_value["body"]["data_bits"] = pParameter->uDataBits;
    json_value["body"]["parity"] = pParameter->uParity;
    json_value["body"]["stop_bits"] = pParameter->uStopBit;
}

bool commandanalysis::GeneratSerialSendCmd(uint32 serial_port, const unsigned char* data, int datalen, Json::Value& json_value) {
    bool ret = false;

    if (serial_port == 0 || serial_port == 1) {
        json_value["cmd"] = "ttransmission";
        json_value["subcmd"] = "send";
        if (serial_port == 0) {
            json_value["comm"] = "rs485-1";
        } else if (serial_port == 1) {
            json_value["comm"] = "rs485-2";
        }

        string result;
        vzsdk::Base64::EncodeFromArray(data, datalen, &result);

        json_value["data"] = result;
        json_value["datalen"] = datalen;

        ret = true;
    }

    return ret;
}

void commandanalysis::GeneratSetOfflineCheckCmd(unsigned int interval, Json::Value& json_value) {
    json_value["cmd"]		= "offline";
    json_value["interval"] = interval;
}

void commandanalysis::GetIOOutput(unsigned int uChnId, Json::Value& json_value) {
    json_value["cmd"]		= "get_gpio_out_value";
    json_value["id"]		= "123456";
    json_value["gpio"] = uChnId;
}

void commandanalysis::GeneratCancelOfflineCheckCmd(Json::Value& json_value) {
    json_value["cmd"]		= "offline";
    json_value["subcmd"]	= "cancel";
}

void commandanalysis::GeneratGetImageByIdCmd(int id, Json::Value& json_value) {
    json_value["cmd"]  = "get_image";
    json_value["id"]   = id;
}

void commandanalysis::GeneratGetRecordByIdCmd(int id, bool needImg, Json::Value& json_value) {
    json_value["cmd"]		= "get_record";
    json_value["id"]		= id;
    json_value["format"]	= "json";
    json_value["image"]	= needImg ? true : false;
}

void commandanalysis::GeneratOfflineCheckCmd(unsigned int interval, Json::Value& json_value) {
    json_value["cmd"] = "offline";
    json_value["interval"] = interval;

}

void commandanalysis::GeneraGetMaxRecordID(Json::Value& json_value) {
    json_value[JSON_REQ_CMD] = JSON_REQ_CMD_GETMAXRECORDID;
}

void commandanalysis::GeneratGetDeviceSN(int _session_id, Json::Value& json_value) {
    json_value[JSON_REQ_CMD] = JSON_REQ_CMD_GETSN;
}

void commandanalysis::GeneratGetRtspUrl(int _session_id, Json::Value& json_value) {
    json_value["cmd"] = "get_rtsp_uri";
    json_value["id"] = _session_id;
}

void commandanalysis::GeneratGetSnapImage(int _session_id, Json::Value& json_value) {
    json_value["cmd"] = "get_snapshot";
    json_value["id"] = _session_id;
}

bool commandanalysis::GeneratPlayVoiceCmd(int _session_id, const char *voice, int voice_len, int interval, int volume, int male, Json::Value& json_value) {
    if (voice == NULL || strlen(voice) == 0) {
        return false;
    }

    if (interval < 0 || interval > 5000) {
        return false;
    }

    if (volume < 0 || volume > 100) {
        return false;
    }

    if (male != 0 && male != 1) {
        return false;
    }

    Json::Value body;

    json_value["cmd"] = "playserver_json_request";
    json_value["id"] = _session_id;

    string encodeVoice;
    vzsdk::Base64::EncodeFromArray(voice, voice_len, &encodeVoice);

    body["type"] = "ps_voice_play";
    body["voice"] = encodeVoice.c_str();
    body["voice_interval"] = interval;
    body["voice_volume"] = volume;
    body["voice_male"] = male;
    json_value["body"] = body;

    return true;
}

void commandanalysis::GeneratGetDateTime( Json::Value& json_value) {
    json_value["cmd"] = "get_device_timestamp";
}
void commandanalysis::GeneratSetDateTime(const VZ_DATE_TIME_INFO *pDTInfo, Json::Value& json_value) {
    char tempstr[100] = { 0 };

    sprintf(tempstr, "%d-%02d-%02d %02d:%02d:%02d", pDTInfo->uYear, pDTInfo->uMonth, pDTInfo->uMDay,\
            pDTInfo->uHour, pDTInfo->uMin, pDTInfo->uSec);

    json_value["cmd"] = "set_time";
    json_value["timestring"] = tempstr;

}

bool commandanalysis::ParseGetDateTime(Json::Value &root, VZ_DATE_TIME_INFO *value) {
    if (!root.isMember("cmd")) {
        return false;
    }
    std::string cmd = root["cmd"].asString();

    if (cmd != "get_device_timestamp" ) {
        return false;
    }

    time_t timestamp = root["timestamp"].asInt();
    struct tm* timeptr;

    timeptr = gmtime(&timestamp);

    value->uYear = timeptr->tm_year;
    value->uYear += 1900;
    value->uMonth = timeptr->tm_mon+1;
    value->uMDay = timeptr->tm_mday;

    value->uHour = timeptr->tm_hour;
    value->uMin = timeptr->tm_min;
    value->uSec = timeptr->tm_sec;


    return true;
}


std::string commandanalysis::IvsFormatToString(int ivs_format) {
    std::string sRet = vzsdk::IVSFormat_JSON;
    switch (ivs_format) {
    case vzsdk::FORMAT_BIN: {
        sRet = vzsdk::IVSFormat_BIN;
        break;
    }
    case vzsdk::FORMAT_JSON: {
        sRet = vzsdk::IVSFormat_JSON;
        break;
    }
    default:
        break;
    }
    return sRet;
}

void commandanalysis::GeneratIVSResult(bool _enable_result, int _format, bool _enable_img, int _img_type, Json::Value& req_json) {
    req_json[JSON_REQ_CMD]                    = JSON_PUSH_CMD_IVSRESULT;
    req_json[JSON_REQ_IVSRESULT_ENABLE]       = _enable_result;
    req_json[JSON_REQ_IVSRESULT_FORMAT]       = commandanalysis::IvsFormatToString(_format);
    req_json[JSON_REQ_IVSRESULT_ENABLE_IMAGE] = _enable_img;
    req_json[JSON_REQ_IVSRESULT_IMAGE_TYPE]   = _img_type;
}

void commandanalysis::GeneratForceTrigger(Json::Value& json_value) {
    json_value[JSON_REQ_CMD] = JSON_REQ_CMD_FORCETRIGGER;
}

void commandanalysis::GeneratGetGPIOValueCmd(int gpio, Json::Value& json_value) {
    json_value["cmd"] = "get_gpio_value";
    json_value["gpio"] = gpio;

}

void commandanalysis::GeneratSetGPIOAutoCmd(int gpio, int duration, Json::Value& json_value) {

    json_value["cmd"] = "ioctl";
    json_value["io"] = gpio;
    json_value["value"] = 2;		//VALUE 0:关，1开，2先通后断
    json_value["delay"] = duration;

}

void commandanalysis::GeneratImportWlistVehicleCmd(const VZ_LPR_WLIST_VEHICLE *item, Json::Value& json_value) {
    // 起效的时间
    char enable_time[64] = {0};
    sprintf(enable_time, "%d-%02d-%02d %02d:%02d:%02d", item->struTMEnable.nYear, item->struTMEnable.nMonth, item->struTMEnable.nMDay,
            item->struTMEnable.nHour, item->struTMEnable.nMin, item->struTMEnable.nSec);

    // 到期时间
    char overdue_time[64] = {0};
    sprintf(overdue_time, "%d-%02d-%02d %02d:%02d:%02d", item->struTMOverdule.nYear, item->struTMOverdule.nMonth, item->struTMOverdule.nMDay,
            item->struTMOverdule.nHour, item->struTMOverdule.nMin, item->struTMOverdule.nSec);

    Json::Value record;

    json_value["cmd"] = "white_list_operator";
    json_value["operator_type"] = "update_or_add";

    record["index"]				= item->uVehicleID;
    record["plate"]				= item->strPlateID;
    record["customer_id"]		= item->uCustomerID;
    record["enable"]			= item->bEnable;
    record["enable_time"]		= enable_time;
    record["create_time"]       = enable_time;
    record["overdue_time"]		= overdue_time;
    record["time_seg_enable"]	= item->bUsingTimeSeg;
    record["need_alarm"]		= item->bAlarm;
    record["vehicle_code"]		= item->strCode;
    record["vehicle_comment"]	= item->strComment;

    json_value["dldb_rec"] = record;
}

void commandanalysis::GeneratDeleteWlistVehicleCmd(const char* plate_code, Json::Value& json_value) {

    json_value["cmd"] = "white_list_operator";
    json_value["operator_type"] = "delete";
    json_value["plate"] = plate_code;
}

void commandanalysis::GeneratQueryWlistVehicleCmd(const char* plate_code, Json::Value& json_value) {

    json_value["cmd"] = "white_list_operator";
    json_value["operator_type"] = "select";
    json_value["sub_type"] = "plate";
    json_value["plate"] = plate_code;
}

void commandanalysis::ParseTTransmissionResponse(Json::Value &root, TTRANSMISSION_RESPONSE &value ) {

    const char *data_temp = NULL;
    int data_len;

    if ( !root["subcmd"].isNull() ) {
        data_temp = root["subcmd"].asCString();
        data_len = strlen(data_temp);
        ASSERT(CMD_LEN > data_len);
        strncpy(value.subcmd, data_temp, data_len);
    }

    if (!root["response"].isNull()) {
        data_temp = root["response"].asCString();
        data_len = strlen(data_temp);
        ASSERT(CMD_LEN > data_len);
        strncpy(value.response, data_temp, data_len);
    }

    if ( !root["datalen"].isNull() ) {
        value.datalen = root["datalen"].asUInt();
    }

    if (!root["comm"].isNull()) {
        value.comm = root["comm"].asUInt();
    }

    if (!root["data"].isNull()) {
        data_temp = root["data"].asCString();
        data_len = strlen(data_temp);
        ASSERT(SEARIL_DATA_LEN > data_len);
        strncpy(value.data, data_temp, data_len);
    }
}

void commandanalysis::ParseOfflineResponse(Json::Value &root, OFFLINE_RESPONSE &value ) {

    const char *data_temp = NULL;
    int data_len;

    if ( !root["subcmd"].isNull() ) {
        data_temp = root["subcmd"].asCString();
        data_len = strlen(data_temp);
        ASSERT(CMD_LEN > data_len);
        strncpy(value.subcmd, data_temp, data_len);
    }

    if (!root["response"].isNull()) {
        data_temp = root["response"].asCString();
        data_len = strlen(data_temp);
        ASSERT(CMD_LEN > data_len);
        strncpy(value.response, data_temp, data_len);
    }
}

void commandanalysis::ParseRecordResponse(Json::Value &root, RECORD_RESPONSE *value ) {
    if ( value == NULL ) {
        return;
    }

    if ( !root["id"].isNull() ) {
        value->id = root["id"].asUInt( );
    }

    if (!root["size"].isNull()) {
        value->size = root["size"].asUInt( );
    }
}

void commandanalysis::ParseGPIOResponse(Json::Value &root, GPIO_RESPONSE *value ) {
    if ( value == NULL ) {
        return;
    }

    if ( !root["gpio"].isNull() ) {
        value->gpio = root["gpio"].asUInt( );
    }

    if (!root["value"].isNull()) {
        value->val = root["value"].asUInt( );
    }
}

void commandanalysis::ParseMaxRecResponse(Json::Value &root, MAX_REC_RESPONSE& value ) {
    if ( !root["max_id"].isNull() ) {
        value.max_id = root["max_id"].asUInt( );
    }
}

void commandanalysis::ParseTimestampResponse(Json::Value &root, TIMESTAMP_RESPONSE *value ) {
    if ( value == NULL ) {
        return;
    }

    if ( !root["timestamp"].isNull() ) {
        value->timestamp = root["timestamp"].asUInt( );
    }
}

void commandanalysis::ParseWhiteListOperatorResponse(Json::Value &root, WHITE_LIST_OPERATOR_RESPONSE &value ) {

    const char *data_temp = NULL;
    int data_len;

    if ( !root["operator_type"].isNull() ) {
        data_temp = root["operator_type"].asCString();
        data_len = strlen(data_temp);
        ASSERT(CMD_LEN > data_len);
        strncpy(value.operator_type, data_temp, data_len);
    }

    if ( !root["state"].isNull() ) {

        data_temp = root["state"].asCString();
        data_len = strlen(data_temp);
        ASSERT(CMD_LEN > data_len);
        strncpy(value.state, data_temp, data_len);
    }

    if ( !root["dldb_rec"].isNull() ) {
        value.record_count = root["dldb_rec"].size();
    }
}

void commandanalysis::ParseWhiteListRecordResponse(Json::Value &item, VZ_LPR_WLIST_VEHICLE &wlist ) {
    if ( item["index"].isInt() ) {
        wlist.uVehicleID = item["index"].asInt();
    }

    if ( item["plate"].isString() ) {
        strncpy( wlist.strPlateID, item["plate"].asCString(), VZ_LPR_WLIST_LP_MAX_LEN - 1 );
    }

    if ( item["ustomer_id"].isInt() ) {
        wlist.uCustomerID		= item["ustomer_id"].asInt();
    }

    if ( item["enable"].isInt() ) {
        wlist.bEnable			= item["enable"].asInt();
    }

    if ( item["enable_time"].isString() ) {
        const char *enable_time = item["enable_time"].asCString();
        sscanf(enable_time, "%d-%d-%d %d:%d:%d", &wlist.struTMEnable.nYear, &wlist.struTMEnable.nMonth,
               &wlist.struTMEnable.nMDay, &wlist.struTMEnable.nHour, &wlist.struTMEnable.nMin, &wlist.struTMEnable.nSec);

        wlist.bEnableTMEnable	= 1;
    }

    if ( item["overdue_time"].isString() ) {
        const char *overdue_time = item["overdue_time"].asCString();
        sscanf(overdue_time, "%d-%d-%d %d:%d:%d", &wlist.struTMOverdule.nYear, &wlist.struTMOverdule.nMonth,
               &wlist.struTMOverdule.nMDay, &wlist.struTMOverdule.nHour, &wlist.struTMOverdule.nMin, &wlist.struTMOverdule.nSec);

        wlist.bEnableTMOverdule = 1;
    }

    if ( item["time_seg_enable"].isInt() ) {
        wlist.bUsingTimeSeg		= item["time_seg_enable"].asInt();
    }

    if ( item["need_alarm"].isInt() ) {
        wlist.bAlarm			= item["need_alarm"].asInt();
    }

    if ( item["vehicle_code"].isString() ) {
        strncpy(wlist.strCode, item["vehicle_code"].asCString(), VZ_LPR_WLIST_VEHICLE_CODE_LEN - 1);
    }

    if( item["vehicle_comment"].isString() ) {
        strncpy(wlist.strComment, item["vehicle_comment"].asCString(), VZ_LPR_WLIST_VEHICLE_COMMENT_LEN - 1);
    }

}

void commandanalysis::ParsePlateResultResponse(Json::Value &root, TH_PlateResult &result, int &fullImgSize, int &clipImgSize) {
    Json::Value& plate = root["PlateResult"];

    if (plate["license"].isString()) {
        strcpy(result.license, plate["license"].asCString());
    }

    if (plate["colorValue"].isString()) {
        strcpy(result.color, plate["colorValue"].asCString());
    }

    if (plate["colorType"].isInt()) {
        result.nColor = plate["colorType"].asInt();
    }

    if (plate["type"].isInt()) {
        result.nType = plate["type"].asInt();
    }

    if (plate["confidence"].isInt()) {
        result.nConfidence = plate["confidence"].asInt();
    }

    if (plate["bright"].isInt()) {
        result.nBright = plate["bright"].asInt();
    }

    if (plate["direction"].isInt()) {
        result.nDirection = plate["direction"].asInt();
    }

    // 车牌位置
    if (plate["location"]["RECT"]["left"].isInt()) {
        result.rcLocation.left = plate["location"]["RECT"]["left"].asInt();
    }

    if (plate["location"]["RECT"]["top"].isInt()) {
        result.rcLocation.top = plate["location"]["RECT"]["top"].asInt();
    }

    if (plate["location"]["RECT"]["right"].isInt()) {
        result.rcLocation.right = plate["location"]["RECT"]["right"].asInt();
    }

    if (plate["location"]["RECT"]["bottom"].isInt()) {
        result.rcLocation.bottom = plate["location"]["RECT"]["bottom"].asInt();
    }

    if (plate["timeUsed"].isInt()) {
        result.nTime = plate["timeUsed"].asInt();
    }

    if (plate["carBright"].isInt()) {
        result.nCarBright = plate["carBright"].asInt();
    }

    if (plate["carColor"].isInt()) {
        result.nCarColor = plate["carColor"].asInt();
    }

    if (plate["timeStamp"]["Timeval"]["sec"].isInt()) {
        result.tvPTS.uTVSec = plate["timeStamp"]["Timeval"]["sec"].asInt();
    }

    if (plate["timeStamp"]["Timeval"]["usec"].isInt()) {
        result.tvPTS.uTVUSec = plate["timeStamp"]["Timeval"]["usec"].asInt();
    }

    if (plate["triggerType"].isInt()) {
        result.uBitsTrigType = plate["triggerType"].asInt();
    }

    if (root["id"].isInt()) {
        result.uId = root["id"].asInt();
    }

    if (root["timeString"].isString()) {
        int nYear = 0, nMonth = 0, nDay = 0, nHour = 0, nMin = 0, nSec = 0;
        const char *timeSting = root["timeString"].asCString();
        sscanf(timeSting, "%d-%d-%d %d:%d:%d", &nYear, &nMonth, &nDay, &nHour, &nMin, &nSec);

        result.struBDTime.bdt_year = nYear;
        result.struBDTime.bdt_mon = nMonth;
        result.struBDTime.bdt_mday = nDay;
        result.struBDTime.bdt_hour = nHour;
        result.struBDTime.bdt_min = nMin;
        result.struBDTime.bdt_sec = nSec;

    }

    if (root["fullImgSize"].isInt()) {
        fullImgSize = root["fullImgSize"].asInt();
    }

    if (root["clipImgSize"].isInt()) {
        clipImgSize = root["clipImgSize"].asInt();
    }
}

bool commandanalysis::GeneratSetIOOutputCmd(int gpio, int nOutput, Json::Value& _json_value) {
    bool ret = false;
    if (nOutput == 0 || nOutput == 1) {
        _json_value["cmd"] = "ioctl";
        _json_value["io"] = gpio;
        _json_value["value"] = nOutput;		//VALUE 0:关，1开
        ret = true;
    }
    return ret;
}

void commandanalysis::GeneratSetVirtualLoops(const VZ_LPRC_VIRTUAL_LOOPS* pVirtualLoops, Json::Value& json_value) {
    Json::Value body,virtualloop,loop,loopitem,point,pointitem;

    for (int i = 0; i < pVirtualLoops->struLoop[0].uNumPoint; i++) {
        pointitem["x"] = IVS_I2S(pVirtualLoops->struLoop[0].struVertex[i].X_1000,1000);
        pointitem["y"] = IVS_I2S(pVirtualLoops->struLoop[0].struVertex[i].Y_1000,1000);

        point.append(pointitem);
    }

    loopitem["id"] = pVirtualLoops->struLoop[0].byID;
    if (pVirtualLoops->struLoop[0].byEnable) {
        loopitem["enable"] = true;
    } else {
        loopitem["enable"] = false;
    }

    loopitem["point_num"] = pVirtualLoops->struLoop[0].uNumPoint;

    loopitem["point"] = point;

    loop.append(loopitem);

    virtualloop["max_plate_width"] = pVirtualLoops->uMaxLPWidth;

    virtualloop["min_plate_width"] = pVirtualLoops->uMinLPWidth;

    virtualloop["dir"] = pVirtualLoops->eCrossDir;

    virtualloop["trigger_gap"] = pVirtualLoops->uTriggerTimeGap;
    virtualloop["virtualloop_num"] = 1;

    virtualloop["loop"] = loop;


    body["virtualloop"]=virtualloop;

    json_value["cmd"] = "set_virloop_para";
    json_value["body"] = body;

}
bool commandanalysis::ParseVirtualLoopsResponse(Json::Value &root, VZ_LPRC_VIRTUAL_LOOPS *pVirtualLoops) {
    std::string cmd;

    if (!root.isMember("cmd")) {
        return false;
    }
    cmd = root["cmd"].asString();
    if (cmd != "get_virloop_para") {
        return false;
    }

    Json::Value body, virtualloop,loop,point;

    body = root["body"];
    virtualloop = body["virtualloop"];

    int uNumVirtualLoop = virtualloop["virtualloop_num"].asInt();

    if (uNumVirtualLoop > VZ_LPRC_VIRTUAL_LOOP_MAX_NUM) {
        return false;
    }

    pVirtualLoops->uMaxLPWidth = virtualloop["max_plate_width"].asInt();
    pVirtualLoops->uMinLPWidth = virtualloop["min_plate_width"].asInt();
    pVirtualLoops->eCrossDir = (VZ_LPRC_DIR)virtualloop["dir"].asInt();
    pVirtualLoops->uTriggerTimeGap = virtualloop["trigger_gap"].asInt();

    pVirtualLoops->uNumVirtualLoop = uNumVirtualLoop;


    loop = virtualloop["loop"];

    if (loop.size() != uNumVirtualLoop) {
        //个数不匹配
        return false;
    }
    int pointnum;
    for (int i = 0; i < uNumVirtualLoop; i++) {
        pVirtualLoops->struLoop[i].byID = loop[i]["id"].asInt();
        pVirtualLoops->struLoop[i].byEnable = (BYTE)loop[i]["enable"].asBool();

        pointnum = loop[i]["point_num"].asInt();

        if (pointnum > VZ_LPRC_VIRTUAL_LOOP_VERTEX_NUM) {
            //个数越界
            return false;
        }
        point = loop[i]["point"];
        pVirtualLoops->struLoop[i].uNumPoint = pointnum;

        if (point.size() != pointnum) {
            //个数不匹配
            return false;
        }

        for (int j = 0; j < pointnum; j++) {
            pVirtualLoops->struLoop[i].struVertex[j].X_1000 = IVS_S2I(point[j]["x"].asInt(),1000);
            pVirtualLoops->struLoop[i].struVertex[j].Y_1000 = IVS_S2I(point[j]["y"].asInt(),1000);
        }
    }

    return true;

}

void commandanalysis::GeneratSetRegionOfInterest(const VZ_LPRC_ROI* pROI, Json::Value& json_value) {
    Json::Value body, recognitionArea, polygon, polygonItem, point, pointitem;

    for (int i = 0; i < pROI->uNumPoint; i++) {
        pointitem["x"] = IVS_I2S(pROI->struVertex[i].X_1000,1000);
        pointitem["y"] = IVS_I2S(pROI->struVertex[i].Y_1000,1000);

        point.append(pointitem);
    }

    polygonItem["id"] = pROI->byId;
    if (pROI->byEnable) {
        polygonItem["enable"] = true;
    } else
        polygonItem["enable"] = false;

    polygonItem["point_num"] = pROI->uNumPoint;

    polygonItem["point"] = point;


    polygon.append(polygonItem);

    recognitionArea["polygon_num"] = 1;

    recognitionArea["polygon"] = polygon;

    body["recognition_area"] = recognitionArea;

    json_value["cmd"] = "set_reco_para";
    json_value["body"] = body;


}
bool commandanalysis::ParseRegionOfInterestResponse(Json::Value &root, VZ_LPRC_ROI *pROI) {
    std::string cmd;

    if (!root.isMember("cmd")) {
        return false;
    }
    cmd = root["cmd"].asString();
    if (cmd != "get_reco_para") {
        return false;
    }

    Json::Value body, recognitionArea, polygon, point;

    body = root["body"];
    recognitionArea = body["recognition_area"];

    int uNumRoi = recognitionArea["polygon_num"].asInt();

    if (uNumRoi <1) {
        return false;
    }

    polygon = recognitionArea["polygon"][0];


    pROI->byEnable = (BYTE)polygon["enable"].asBool();
    pROI->byId = polygon["id"].asInt();

    int uNumPoint = polygon["point_num"].asInt();

    if (uNumPoint > VZ_LPRC_ROI_VERTEX_NUM) {
        //个数越界
        return false;
    }

    pROI->uNumPoint = uNumPoint;

    point = polygon["point"];
    for (int i = 0; i < uNumPoint; i++) {
        pROI->struVertex[i].X_1000 = IVS_S2I(point[i]["x"].asInt(),1000);
        pROI->struVertex[i].Y_1000 = IVS_S2I(point[i]["y"].asInt(),1000);
    }

    return true;
}

void commandanalysis::GeneratSetNetParamCmd(const VZ_LPRC_NETCFG*pNetParam, Json::Value& json_value) {
    Json::Value body;

    body["ip"] = pNetParam->ipaddr;
    body["netmask"] = pNetParam->netmask;
    body["gateway"] = pNetParam->gateway;
    body["dns"] = pNetParam->dns;

    json_value["cmd"] = "set_networkparam";
    json_value["id"] = "123456";
    json_value["body"] = body;
}

bool commandanalysis::ParseNetParamResponse(Json::Value &root, VZ_LPRC_NETCFG *pNetParam) {
    std::string cmd;

    if (!root.isMember("cmd")) {
        return false;
    }
    cmd = root["cmd"].asString();
    if (cmd != "get_networkparam") {
        return false;
    }

    int state_code = root["state_code"].asInt();

    if (state_code!= 200) {
        return false;
    }

    Json::Value body = root["body"];

    std::string temp = body["ip"].asString();
    int len =  temp.size();
    if (len >= LPRC_IPLEN) {
        return false;
    }
    memcpy(pNetParam->ipaddr, temp.c_str(), len);

    temp = body["gateway"].asString();
    len = temp.size();
    if (len >= LPRC_IPLEN) {
        return false;
    }
    memcpy(pNetParam->gateway, temp.c_str(), len);

    temp = body["netmask"].asString();
    len = temp.size();
    if (len >= LPRC_IPLEN) {
        return false;
    }
    memcpy(pNetParam->netmask, temp.c_str(), len);

    temp = body["dns"].asString();
    len = temp.size();
    if (len >= LPRC_IPLEN) {
        return false;
    }
    memcpy(pNetParam->dns, temp.c_str(), len);

    return true;
}

//手动聚焦变倍
void commandanalysis::StartFocusAndZoomCmd(int value, Json::Value& json_value) {
    Json::Value body;

    body["value"] = value;


    json_value["cmd"] = "startfocusandzoom";
    json_value["id"] = "123456";
    json_value["body"] = body;
}
void commandanalysis::StopFocusAndZoom(int value, Json::Value& json_value) {
    Json::Value body;

    body["value"] = value;

    json_value["cmd"] = "stopfocusandzoom";
    json_value["id"] = "123456";
    json_value["body"] = body;
}

//算法识别参数
void commandanalysis::SetAlgResultPara(const VZ_ALG_RESULT_PARAM * param, Json::Value& json_value) {
    Json::Value body;

    body["snap_resolution"] = param->snapResolution;
    body["snap_image_quality"] = param->snapImageQuality;
    body["out_result_type"] = param->outResultType;
    body["recognition_type"] = param->RecognitionType;
    body["province"] = param->province;
    body["run_mode"] = param->runMode;
    body["alg_version"] = param->algVersion;
    body["time_zone"] = param->timeZone;
    body["reco_dis"] = param->recoDis;

    json_value["cmd"] = "set_alg_result_para";
    json_value["id"] = "123456";
    json_value["body"] = body;
}
bool commandanalysis::ParseAlgResultParaResponse(Json::Value &root, VZ_ALG_RESULT_PARAM *param) {
    std::string cmd;

    if (!root.isMember("cmd")) {
        return false;
    }
    cmd = root["cmd"].asString();
    if (cmd != "get_alg_result_para") {
        return false;
    }

    int state_code = root["state_code"].asInt();

    if (state_code != 200) {
        return false;
    }

    Json::Value body = root["body"];

    param->snapResolution = body["snap_resolution"].asInt();
    param->snapImageQuality = body["snap_image_quality"].asInt();
    param->outResultType = body["out_result_type"].asInt();
    param->RecognitionType = body["recognition_type"].asInt();
    param->province = body["province"].asInt();
    param->runMode = body["run_mode"].asInt();

    param->timeZone = body["time_zone"].asInt();
    param->recoDis = body["reco_dis"].asInt();

    std::string text =  body["alg_version"].asString();
    memcpy(param->algVersion, text.c_str(), text.size());

    return true;
}

//LED参数
void commandanalysis::SetLedPara(const VZ_LED_PARAM * param, Json::Value& json_value) {
    Json::Value body,timectrl,timectrlitem;


    for (int i = 0; i < TIME_CTRL_MAX_SIZE; i++) {
        timectrlitem["id"] = i;
        timectrlitem["led_level"] = param->timeCtrl[i].ledLevel;
        timectrlitem["time_begin"] = param->timeCtrl[i].time_begin;
        timectrlitem["time_end"] = param->timeCtrl[i].time_end;
        if (param->timeCtrl[i].enable) {
            timectrlitem["timectrl_enable"] = true;
        } else
            timectrlitem["timectrl_enable"] = false;

        timectrl.append(timectrlitem);
    }

    body["led_level"] = param->ledLevel;
    body["led_mode"] = param->ledMode;
    body["time_ctrl"] = timectrl;

    json_value["cmd"] = "set_led_para";
    json_value["id"] = "123456";
    json_value["body"] = body;
}
bool commandanalysis::ParseLedParaResponse(Json::Value &root, VZ_LED_PARAM *param) {
    std::string cmd;

    if (!root.isMember("cmd")) {
        return false;
    }
    cmd = root["cmd"].asString();
    if (cmd != "get_led_para") {
        return false;
    }

    int state_code = root["state_code"].asInt();

    if (state_code != 200) {
        return false;
    }

    Json::Value body = root["body"];
    Json::Value timectrl, timectrlitem;

    timectrl = body["time_ctrl"];

    int itemCount = timectrl.size();

    if (itemCount > TIME_CTRL_MAX_SIZE) {
        return false;
    }
    std::string text;
    bool enable;
    for (int i = 0; i < itemCount; i++) {
        timectrlitem = timectrl[i];

        param->timeCtrl[i].ledLevel = timectrlitem["led_level"].asInt();

        text = timectrlitem["time_begin"].asString();
        memcpy(param->timeCtrl[i].time_begin, text.c_str(), text.size());

        text = timectrlitem["time_end"].asString();
        memcpy(param->timeCtrl[i].time_end, text.c_str(), text.size());

        enable = timectrlitem["timectrl_enable"].asBool();
        if (enable) {
            param->timeCtrl[i].enable = 1;
        } else
            param->timeCtrl[i].enable = 0;


    }

    param->ledLevel = body["led_level"].asInt();
    param->ledMode = body["led_mode"].asInt();


    return true;
}

void commandanalysis::RestorConfigCmd(int value, Json::Value& json_value) {
    Json::Value body;

    body["factorydefault"] = value;

    json_value["cmd"] = "set_factorydefault";
    json_value["id"] = "123456";
    json_value["body"] = body;
}

void commandanalysis::WriteUserData(std::string data,
                                    Json::Value& json_value) {
    Json::Value body;

    body["data"] = data;

    json_value["cmd"] = "set_user_data";
    json_value["id"] = "123456";
    json_value["body"] = body;
}

void commandanalysis::ReadUserData(    Json::Value& json_value) {
    json_value["cmd"] = "get_user_data";
    json_value["id"] = "123456";
}

void commandanalysis::GetStorageDeviceInfo(    Json::Value& json_value) {
    json_value["cmd"] = "get_diskinfo";
    json_value["id"] = "123456";
}
