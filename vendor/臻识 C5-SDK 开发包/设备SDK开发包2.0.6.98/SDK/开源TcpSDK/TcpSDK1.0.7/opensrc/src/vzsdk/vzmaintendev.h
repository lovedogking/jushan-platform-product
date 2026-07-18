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
#ifndef SRC_HSHA_MAINTEN_H_
#define SRC_HSHA_MAINTEN_H_

#include "vzsdk/queuelayer.h"
#include "vzsdk/vzmodulebase.h"

using namespace vzsdk;

namespace vzsdk {


#define MAX(A, B) (((A) > (B)) ? (A) : (B))
#define MIN(A, B) (((A) < (B)) ? (A) : (B))

#define MEMCPY_SAFE(dst, dst_len, src, src_len) memcpy(dst, src, \
                                                MIN((dst_len), (src_len)));

class VzMaintenDev : public VZModuleBase {
  public:
    VzMaintenDev(VzsdkService* _service);
    ~VzMaintenDev();

    int GetDeviceSN(std::string &sn);

    int GetRtspUrl(std::string &url);

    int GetDateTime(VZ_DATE_TIME_INFO *pDTInfo);
    int SetDateTime(const VZ_DATE_TIME_INFO *pDTInfo);

    int SetNetworkParam(const VZ_LPRC_NETCFG * pNetParam);
    int GetNetworkParam(VZ_LPRC_NETCFG * pNetParam);

    int PlaySound(const char *voice, int voice_len, int interval, int volume, int male);

    //自动变倍聚焦
    int AutoFocus();
    //变倍聚焦
    int CtrlLens(int value);


    int GetAlgResultParam(VZ_ALG_RESULT_PARAM * param);
    int SetAlgResultParam(const VZ_ALG_RESULT_PARAM * param);

    int SetLedParam(const VZ_LED_PARAM * param);
    int GetLedParam(VZ_LED_PARAM* param);

    int RestoreConfig();
    int RestoreConfigPartly();

    int WriteUserData(const unsigned char *pUserData, unsigned uSizeData);
    int ReadUserData(unsigned char *pBuffer, unsigned uSizeBuf);

    int GetStorageDeviceInfo(VZ_STORAGE_DEVICES_INFO *pSDInfo);
    
};
}

#endif