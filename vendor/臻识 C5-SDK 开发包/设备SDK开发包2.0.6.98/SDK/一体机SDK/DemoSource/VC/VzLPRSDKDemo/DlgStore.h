#pragma once

#include "VzLPRClientSDKDefine.h"
// CDlgStore dialog

class CDlgStore : public CDialog
{
	DECLARE_DYNAMIC(CDlgStore)

public:
	CDlgStore(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgStore();

// Dialog Data
	enum { IDD = IDD_DIALOG_STORE };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

	private:
	VzLPRClientHandle m_hLPRClient;
	VZ_STORAGE_DEVICES_INFO  m_sdi;
	int    m_SelectStoreDev;
public:
	void SetLPRHandle(VzLPRClientHandle hLPRClient);

	
	virtual BOOL OnInitDialog();
	CListCtrl m_ListStoreDevPartion;
	CComboBox m_CbStoreNum;

	afx_msg void OnBnClickedButtonFomartSd();
	
	void SelectStoreDev( int iNum);
	afx_msg void OnCbnSelchangeComboStoreNum();

	bool   GetStoreDeviceInfo();

	void   UpdateControl();

};
