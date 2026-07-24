#pragma once
#include "VzLPRClientSDKDefine.h"
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

// DlgDeviceInfo dialog

class DlgDeviceInfo : public CDialog
{
	DECLARE_DYNAMIC(DlgDeviceInfo)

public:
	DlgDeviceInfo(CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgDeviceInfo();

// Dialog Data
	enum { IDD = IDD_DLG_DEVICE_INFO };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	void SetLPRHandle(VzLPRClientHandle hLPRClient);
	virtual BOOL  OnInitDialog();
	BOOL InitDevInfo();
protected:
    VzLPRClientHandle m_hLPRClient;
	char m_strModuleDir[MAX_PATH];

public:
	 
};
