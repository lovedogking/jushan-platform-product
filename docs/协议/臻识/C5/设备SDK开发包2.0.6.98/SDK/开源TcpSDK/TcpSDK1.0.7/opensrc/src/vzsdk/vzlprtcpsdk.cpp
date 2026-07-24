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
#include "vzsdk/vzlprtcpsdk.h"
#include "base/criticalsection.h"
#include "base/vzfile.h"

#include "vztcpdevicemanage.h"
#include "vzsdk/vzsdkdefines.h"

#include "vzconnectdev.h"
#include "vzrecognition.h"
#include "vzmaintendev.h"
#include "vzserialdev.h"
#include "vzwlistdev.h"
#include "vziodev.h"
#include <string.h>
#include "base/socket.h"
#include "DeviceFinderManage.h"
#include "base/changedevicenetwork.h"


using namespace std;
using namespace vzsdk;
vzsdk::CriticalSection scoped_lock;
VzTcpDeviceManage* vztcp_device_manage = NULL;

const int _vzsdk_success  = 0;
const int _vzsdk_failed   = -1;
DeviceFinderManage device_finder;

VZ_LPRC_LASTERROR_TYPE g_LastError;
void SetLastError(int ret, VZ_LPRC_LASTERROR_TYPE eLastErrorType) {
    if (ret != _vzsdk_success)
        g_LastError = eLastErrorType;
}

bool CheckManage() {
    int ret = vztcp_device_manage != NULL ? _vzsdk_success : _vzsdk_failed;
    SetLastError(ret, VZ_LPRC_LASTERROR_NOT_SETUP);
    return ret == _vzsdk_success;
}

bool CheckDevice(VzLPRTcpHandle handle) {
    bool exist_manage = CheckManage();
    bool ret = exist_manage;
    if (exist_manage) {
        VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
        if (_serveres)
            ret = true;
        else
            SetLastError(_vzsdk_failed, VZ_LPRC_LASTERROR_INVALID_HANDLE);
    }
    return ret;
}

int __STDCALL VzLPRTcp_Setup() {

    CritScope _critscope(&scoped_lock);

    int _ret = _vzsdk_failed;

    vztcp_device_manage = new VzTcpDeviceManage;
    if (vztcp_device_manage) {
        _ret = _vzsdk_success;
        vztcp_device_manage->Start();
    }

    if (_ret == _vzsdk_failed) {
        LOG(INFO) << "Tcp Steup Fail";
    } else {
        LOG(INFO) << "Tcp Steup Success";
    }

    return _ret;
}

void __STDCALL VzLPRTcp_Cleanup() {
    CritScope _critscope(&scoped_lock);

    if (vztcp_device_manage) {
        vztcp_device_manage->Stop();
        delete vztcp_device_manage;
        vztcp_device_manage = NULL;
    }


    LOG(INFO) << "Tcp Cleanup";

}

VzLPRTcpHandle __STDCALL VzLPRTcp_Open(const char *pStrIP, WORD wPort, const char *pStrUserName, const char *pStrPassword) {
    CritScope _critscope(&scoped_lock);
    int ret = _vzsdk_failed;
    ret = vztcp_device_manage->CreateNewService(pStrIP, wPort, pStrUserName, pStrPassword);
    SetLastError(ret, VZ_LPRC_LASTERROR_CREATE_FAILED);
    return ret > 0 ? ret : 0;
}

int __STDCALL VzLPRTcp_Close(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    ret = vztcp_device_manage->CloseService(handle) ? _vzsdk_success : _vzsdk_failed;
    return ret;
}

int __STDCALL VzLPRTcp_CloseByIP(const char *pStrIP) {
    int ret = _vzsdk_failed;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(pStrIP);
    if (_serveres) {
        vzsdk::VzConnectDevPtr _connect_ptr = _serveres->GetConnectDev();
        VzLPRTcpHandle _session_id = _connect_ptr->GetSessionID();
        if (_session_id != NULL_SESSION_ID)
            ret = VzLPRTcp_Close(_session_id);
    }
    SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_IP);
    return ret;
}

int __STDCALL VzLPRTcp_GetDeviceSN(VzLPRTcpHandle handle, char* pSN) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    if (pSN == NULL) {
        SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_PARAM);
        return ret;
    }

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        string _device_sn;
        VzMaintenDevPtr _maintendev = _serveres->GetMaintenDev();
        ret = _maintendev->GetDeviceSN(_device_sn);
        if (ret == _vzsdk_success)
            memcpy(pSN, _device_sn.c_str(), _device_sn.length());
    }
    SetLastError(ret, VZ_LPRC_LASTERROR_GETDEVICE_ERROR);
    return ret;
}

int __STDCALL VzLPRTcp_IsConnected(VzLPRTcpHandle handle, BYTE *pStatus) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    if (pStatus == NULL) {
        SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_PARAM);
        return ret;
    }

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        int _conn_state = _serveres->GetConnectDev()->GetConnState();
        *pStatus = (_conn_state == 2) ? true : false;
        ret = _vzsdk_success;
    }
    SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_PSTATUS);
    return ret;
}

int __STDCALL VzLPRTcp_SetPlateInfoCallBack(VzLPRTcpHandle handle
        , VZLPRC_TCP_PLATE_INFO_CALLBACK func
        , void *pUserData
        , int bEnableImage) {

    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzRecognitionPtr _recongnition_ptr = _serveres->GetRecongintion();
        if (_recongnition_ptr) {
            ret = _recongnition_ptr->SetReciveIvsResultCallback(func, pUserData, bEnableImage);
        }
    }
    return ret;
}

int __STDCALL VzLPRTcp_ImageSaveToJpeg(unsigned char *pImgBuf
                                       , int nImgSize
                                       , const char *pSavePathName
                                       , int nQuality) {

    int ret = _vzsdk_failed;
    if (pImgBuf == NULL || nImgSize <= 0 || pSavePathName == NULL || strlen(pSavePathName) == 0) {
        SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_PARAM);
        return _vzsdk_failed;
    }

    int _ret = _vzsdk_failed;

    bool _write_ret = CVzFile::WriteFile(pSavePathName, pImgBuf, nImgSize);
    _ret = _write_ret ? _vzsdk_success : _vzsdk_failed;
    return _ret;
}

int __STDCALL VzLPRTcp_ForceTrigger(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzRecognitionPtr _reconginition_ptr = _serveres->GetRecongintion();
        ret = _reconginition_ptr->ForceTrigger();
    }
    return ret;
}

int __STDCALL VzLPRTcp_GetLastError() {
    return (int)g_LastError;
}

int __STDCALL VzLPRTcp_LoadRecordById(VzLPRTcpHandle handle, int id, TH_PlateResult *pResult) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzRecognitionPtr _reconginition_ptr = _serveres->GetRecongintion();
        int full_size, clip_size;
        ret = _reconginition_ptr->GetRecord(id, false, *pResult
                                            , full_size, NULL
                                            , clip_size, NULL);
    } else
        SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_HANDLE);

    return ret;
}

const char* __STDCALL VzLPRTcp_GetIP(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return NULL;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        static std::string _device_ip = _serveres->GetConnectDev()->GetIP();
        return _device_ip.c_str();
    }
    return NULL;
}

int __STDCALL VzLPRTcp_SetCommonNotifyCallBack(VZLPRC_TCP_COMMON_NOTIFY_CALLBACK func, void *pUserData) {
    int ret = _vzsdk_failed;
    if (!CheckManage())
        return _vzsdk_failed;
    vztcp_device_manage->SetCommonNotifyCallBack(func, pUserData);
    return ret;
}


int __STDCALL VzLPRTcp_LoadImageById(VzLPRTcpHandle handle, int id, void *pdata, int* size) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;
    if (pdata == NULL || size == NULL) {
        SetLastError(ret, VZ_LPRC_LASTERROR_INVALID_PARAM);
        return ret;
    }
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzRecognitionPtr _reconginition_ptr = _serveres->GetRecongintion();
        ret = _reconginition_ptr->GetImage(id, (char*)pdata, *size);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SerialStart(VzLPRTcpHandle handle, int nSerialPort,
                                   VZDEV_TCP_SERIAL_RECV_DATA_CALLBACK func, void *pUserData) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzSerialDevPtr _serial_ptr = _serveres->GetSerialDev();
        ret = _serial_ptr->SerialStart(nSerialPort);
        _serial_ptr->SetSerialRecvCallBack(func, pUserData);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SerialSend(VzLPRTcpHandle handle, int nSerialPort, const unsigned char *pData, unsigned uSizeData) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzSerialDevPtr _serial_ptr = _serveres->GetSerialDev();
        ret = _serial_ptr->SerialSend(nSerialPort, pData, uSizeData);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SerialStop(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzSerialDevPtr _serial_ptr = _serveres->GetSerialDev();
        ret = _serial_ptr->SerialStop();
    }
    return ret;
}

int __STDCALL VzLPRTcp_SetIOOutputAuto(VzLPRTcpHandle handle, unsigned uChnId, int nDuration) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzIODevPtr _io_ptr = _serveres->GetIODev();
        ret = _io_ptr->SetIOOutputAuto(uChnId, nDuration);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SetIOOutput(VzLPRTcpHandle handle, unsigned uChnId, int nOutput) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzIODevPtr _io_ptr = _serveres->GetIODev();
        ret = _io_ptr->SetIOOutput(uChnId, nOutput);
    }
    return ret;
}

int __STDCALL VzLPRTcp_GetGPIOValue(VzLPRTcpHandle handle, int gpioIn, int *value) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzIODevPtr _io_ptr = _serveres->GetIODev();
        ret = _io_ptr->GetGPIOValue(gpioIn, value);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SetOfflineCheck(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzIODevPtr _io_ptr = _serveres->GetIODev();
        ret = _io_ptr->SetOfflineCheck();
    }
    return ret;
}

int __STDCALL VzLPRTcp_CancelOfflineCheck(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzIODevPtr _io_ptr = _serveres->GetIODev();
        ret = _io_ptr->CancelOfflineCheck();
    }
    return ret;
}

int __STDCALL VzLPRTcp_ImportWlistVehicle(VzLPRTcpHandle handle, VZ_LPR_WLIST_VEHICLE *pWlist) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzWlistDevPtr _wlist_ptr = _serveres->GetWlistDev();
        ret = _wlist_ptr->ImportWlistVehicle(pWlist);
    }
    return ret;
}

int __STDCALL VzLPRTcp_DeleteWlistVehicle(VzLPRTcpHandle handle, const char* plateCode) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzWlistDevPtr _wlist_ptr = _serveres->GetWlistDev();
        ret = _wlist_ptr->DeleteWlistVehicle(plateCode);
    }
    return ret;
}

int __STDCALL VzLPRTcp_QueryWlistVehicle(VzLPRTcpHandle handle, const char* plateCode) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzWlistDevPtr _wlist_ptr = _serveres->GetWlistDev();
        ret = _wlist_ptr->QueryWlistVehicle(plateCode);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SetWlistInfoCallBack(VzLPRTcpHandle handle
        , VZLPRC_TCP_WLIST_RECV_CALLBACK func
        , void *pUserData) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        VzWlistDevPtr _wlist_ptr = _serveres->GetWlistDev();
        _wlist_ptr->SetWlistRecvCallBack(func, pUserData);

        ret = _vzsdk_success;
    }
    return ret;
}

int __STDCALL VzLPRTcp_SetOfflinePlateInfoCallBack(VzLPRTcpHandle handle
        , VZLPRC_TCP_PLATE_INFO_CALLBACK func
        , void *pUserData
        , int bEnableImage) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret =  _serveres->GetRecongintion()->SetResumRecordCallback(func, pUserData, bEnableImage);
    }

    return ret;
}


int __STDCALL VzLPRTcp_SetReciveGpioCallback(VzLPRTcpHandle handle, VZLPRC_GPIO_RECV_CALLBACK func, void* pUserData) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetIODev()->SetReciveGpioCallback(func, pUserData);
    }

    return ret;
}


int __STDCALL VzLPRTcp_GetSnapImageData(VzLPRTcpHandle handle, char * imgBuffer, int imgBufferMaxLength) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetRecongintion()->GetSnapImage(imgBuffer, imgBufferMaxLength);
    }

    return ret;
}

int __STDCALL VzLPRTcp_GetRtspUrl(VzLPRTcpHandle handle,  char *url,int urlMaxLength) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres && url) {
        std::string tempurl;
        ret = _serveres->GetMaintenDev()->GetRtspUrl(tempurl);

        memcpy(url, tempurl.c_str(), urlMaxLength);
    }

    return ret;
}

int __STDCALL VzLPRTcp_PlayVoice(VzLPRTcpHandle handle, const char *voice, int voice_len, int interval, int volume, int male) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->PlaySound(voice, voice_len, interval, volume, male);
    }
    return ret;
}


int __STDCALL VzLPRTcp_GetDateTime(VzLPRTcpHandle handle, VZ_DATE_TIME_INFO *pDTInfo) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->GetDateTime(pDTInfo);
    }
    return ret;
}


int __STDCALL VzLPRTcp_SetDateTime(VzLPRTcpHandle handle, const VZ_DATE_TIME_INFO *pDTInfo) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->SetDateTime(pDTInfo);
    }
    return ret;
}


int __STDCALL VzLPRTcp_GetVirtulLoop(VzLPRTcpHandle handle, VZ_LPRC_VIRTUAL_LOOPS *pVirtualLoops) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetRecongintion()->GetVirtulLoop(pVirtualLoops);
    }

    return ret;
}


int __STDCALL VzLPRTcp_SetVirtulLoop(VzLPRTcpHandle handle, const VZ_LPRC_VIRTUAL_LOOPS *pVirtualLoops) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetRecongintion()->SetVirtulLoop(pVirtualLoops);
    }

    return ret;
}

int __STDCALL VzLPRTcp_GetRegionOfInterest(VzLPRTcpHandle handle, VZ_LPRC_ROI * pROI) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetRecongintion()->GetRegionOfInterest(pROI);
    }

    return ret;
}


int __STDCALL VzLPRTcp_SetRegionOfInterest(VzLPRTcpHandle handle, const VZ_LPRC_ROI * pROI) {
    if (!CheckDevice(handle))
        return _vzsdk_failed;
    int ret = _vzsdk_failed;
    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetRecongintion()->SetRegionOfInterest(pROI);
    }

    return ret;
}

int __STDCALL VzLPRTcp_SetNetworkParam(VzLPRTcpHandle handle, const VZ_LPRC_NETCFG * pNetParam) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->SetNetworkParam(pNetParam);
    }
    return ret;
}


int __STDCALL VzLPRTcp_GetNetworkParam(VzLPRTcpHandle handle, VZ_LPRC_NETCFG * pNetParam) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->GetNetworkParam(pNetParam);
    }
    return ret;
}


int __STDCALL VzLPRTcp_AutoFocus(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->AutoFocus();
    }
    return ret;
}

int __STDCALL VzLPRTcp_CtrlLens(VzLPRTcpHandle handle, int value) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->CtrlLens(value);
    }
    return ret;
}


int __STDCALL VzLPRTcp_GetAlgResultParam(VzLPRTcpHandle handle, VZ_ALG_RESULT_PARAM * param) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->GetAlgResultParam(param);
    }
    return ret;
}


int __STDCALL VzLPRTcp_SetAlgResultParam(VzLPRTcpHandle handle, const VZ_ALG_RESULT_PARAM * param) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->SetAlgResultParam(param);
    }
    return ret;
}

int __STDCALL VzLPRTcp_SetLedParam(VzLPRTcpHandle handle, const VZ_LED_PARAM * param) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->SetLedParam(param);
    }
    return ret;
}


int __STDCALL VzLPRTcp_GetLedParam(VzLPRTcpHandle handle, VZ_LED_PARAM* param) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->GetLedParam(param);
    }
    return ret;
}


int __STDCALL VzLPRTcp_RestoreConfig(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->RestoreConfig();
    }
    return ret;
}


int __STDCALL VzLPRTcp_RestoreConfigPartly(VzLPRTcpHandle handle) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->RestoreConfigPartly();
    }
    return ret;
}

int __STDCALL VzLPRTcp_WriteUserData(VzLPRTcpHandle handle,
                                     const unsigned char *pUserData,
                                     unsigned uSizeData) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->WriteUserData(pUserData, uSizeData);
    }

    return ret;
}

int __STDCALL VzLPRTcp_ReadUserData(VzLPRTcpHandle handle,
                                    unsigned char *pBuffer,
                                    unsigned uSizeBuf) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->ReadUserData(pBuffer, uSizeBuf);
    }

    return ret;
}

int __STDCALL VzLPRTcp_GetStorageDeviceInfo(VzLPRTcpHandle handle,
        VZ_STORAGE_DEVICES_INFO *pSDInfo) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetMaintenDev()->GetStorageDeviceInfo(pSDInfo);
    }

    return ret;
}

int __STDCALL VzLPRTcp_GetSerialParameter(VzLPRTcpHandle handle,
        int nSerialPort,
        VZ_SERIAL_PARAMETER *pParameter) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetSerialDev()->GetSerialParameter(nSerialPort, pParameter);
    }

    return ret;
}

int __STDCALL VzLPRTcp_SetSerialParameter(VzLPRTcpHandle handle,
        int nSerialPort,
        const VZ_SERIAL_PARAMETER *pParameter) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetSerialDev()->SetSerialParameter(nSerialPort, pParameter);
    }

    return ret;
}

int __STDCALL VzLPRTcp_GetIOOutput(VzLPRTcpHandle handle,
                                   unsigned uChnId,
                                   int *pOutput) {
    int ret = _vzsdk_failed;
    if (!CheckDevice(handle))
        return ret;

    VzsdkServicesPtr _serveres = vztcp_device_manage->GetService(handle);
    if (_serveres) {
        ret = _serveres->GetIODev()->GetIOOutput(uChnId, pOutput);
    }

    return ret;
}

int __STDCALL VzLPRTcp_UpdateNetworkParam(unsigned int SL,
        unsigned int SH,
        const char* strNewIP,
        const char* strGateway,
        const char *strNetmask) {
    int res = _vzsdk_failed;
    if (strNewIP == NULL || strGateway == NULL || strNetmask == NULL) {
        return _vzsdk_failed;
    }

    // 检测网络参数是否正确，要求IP和网关在同一个网段
    int rightIPAddr = VerifyIpaddrGatawayCorrect(strNewIP, strGateway, strNetmask);
    if (rightIPAddr != CHANGE_SUCCEED) {
        return _vzsdk_failed;
    }

    unsigned long lNewIP = inet_addr(strNewIP);
    unsigned long lGateway = inet_addr(strGateway);
    unsigned long lMask = inet_addr(strNetmask);

    int ret = ChangeDeviceNetwork(SH, SL, "228.5.6.2", 24100, lNewIP, lGateway, lMask);
    if (ret != CHANGE_SUCCEED) {
        return _vzsdk_failed;
    }

    return _vzsdk_success;
}

        
int __STDCALL VzLPRTcp_StartFindDevice(VZLPRC_FIND_DEVICE_CALLBACK func, void *pUserData) {
        bool res = device_finder.StartFindDevice(func, pUserData); 
    if (res) {
        return _vzsdk_success;
    }
    return _vzsdk_failed;
}

void __STDCALL VZLPRTcp_StopFindDevice() {
	device_finder.StopFindDevice();
}




