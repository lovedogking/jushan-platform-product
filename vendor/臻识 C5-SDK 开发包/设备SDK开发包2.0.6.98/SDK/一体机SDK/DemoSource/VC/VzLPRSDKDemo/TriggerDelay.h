#pragma once

#include "afxwin.h"
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "DlgOutputAndInBase.h"
#include "Resource.h"

// CTriggerDelay dialog

class CTriggerDelay : public CDlgOutputAndInBase
{
	DECLARE_DYNAMIC(CTriggerDelay)

public:
	CTriggerDelay();   // standard constructor
	virtual ~CTriggerDelay();
	
	virtual void refresh();
	int IDD() { return IDD_DLG_TriggerDelay; };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

private:
	bool m_bCompEnable;
	int m_nTriDelay;
	int m_nBlastLast;
	int m_nBlastDelay;
    char m_szGpioIn[4];
	char m_szGpioOut[7];
	char m_sTriDelay[8] ;
	char m_sBlastLast[8] ;
	char m_sBlastDelay[8] ;


public:
	afx_msg void OnBnClickedOk();
	virtual BOOL OnInitDialog();
};
