#include "DeviceFinderManage.h"
#include <stdio.h>
#include "vzlprtcpsdk.h" 


namespace vzsdk {

DeviceFinderManage::DeviceFinderManage() {
    run_flag_ = false;
    callback_ = NULL;
    user_data_ = NULL;

}

DeviceFinderManage::~DeviceFinderManage() {

}

bool DeviceFinderManage::StartFindDevice(VZLPRC_FIND_DEVICE_CALLBACK call_back, void *user_data) {
    if (run_flag_) {
        return false;
    }
    bool res = false;

    callback_ = call_back;
    user_data_ = user_data;
    ipGroup_.clear();

    res = LprDeviceFinder_.StartFindDevice(DeviceFinderManage::OnFindDeviceCallback, this);

    if (res) {
        run_flag_ = true;
    }

    return res;
}

void DeviceFinderManage::StopFindDevice() {
    if (!run_flag_) {
        return  ;
    }

    run_flag_ = false;

    LprDeviceFinder_.StopFindDevice();
    callback_ = NULL;
    user_data_ = NULL;
}

bool DeviceFinderManage::IsExist(const std::string &text) {
    for (int i = 0; i < ipGroup_.size(); i++) {
        if (ipGroup_[i] == text) {
            return true;
        }
    }

    ipGroup_.push_back(text);

    return false;
}

void __STDCALL   DeviceFinderManage::OnFindDeviceCallback(const char *pStrDevName,
        const char *pStrIPAddr, WORD usPort1,
        WORD usPort2, unsigned int SL, unsigned int SH,
        char* netmask, char* gateway, void *pUserData) {
    DeviceFinderManage *manage = (DeviceFinderManage*)pUserData;

    if (manage && manage->callback_) {
        LockGuard<Mutex> lock(&manage->mutex_);

        char slshtext[50] = { 0 };

        sprintf(slshtext, "%d.%d", SL, SH);

        if (!manage->IsExist(slshtext)) {
            manage->callback_(pStrDevName, pStrIPAddr, usPort1, usPort2, SL, SH, netmask,gateway, manage->user_data_);
        }
    }
}

}
