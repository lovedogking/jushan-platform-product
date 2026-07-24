#pragma once
#include "DlgOutputAndInBase.h"
#include "VzLPRClientSDKDefine.h"
#include <map>
#include "Resource.h"


// CSpecialPlatesCfg dialog

class CSpecialPlatesCfg : public CDlgOutputAndInBase
{
	DECLARE_DYNAMIC(CSpecialPlatesCfg)

public:
	CSpecialPlatesCfg(CWnd* pParent = NULL);   // standard constructor
	virtual ~CSpecialPlatesCfg();

// Dialog Data
	//enum { IDD = IDD_DLG_SpecialPlates };

	int IDD() { return IDD_DLG_SpecialPlates; };
	virtual void refresh();		//¶Ô»°¿òË¢ÐÂ

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedOk();
};
