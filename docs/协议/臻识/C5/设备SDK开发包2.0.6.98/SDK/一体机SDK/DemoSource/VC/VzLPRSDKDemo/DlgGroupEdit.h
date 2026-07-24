#pragma once
#include "afxcmn.h"
#include "afxwin.h"
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
// CDlgGroupEdit dialog

class CDlgGroupEdit : public CDialog
{
	DECLARE_DYNAMIC(CDlgGroupEdit)

public:
	CDlgGroupEdit(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgGroupEdit();

// Dialog Data
	enum { IDD = IDD_DLG_GROUP_EDIT };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	virtual BOOL OnInitDialog();
	DECLARE_MESSAGE_MAP()

public:
	void SetDeviceInfo(CString strName, CString strIP, int nType, CString strSerialNO, CString strGroupName, BOOL bEnable);
	void SetOvzidResult(VZ_OVZID_RESULT *pData, int nIndex);
	afx_msg void OnBnClickedOk();
	void SetLPRHandle( VzLPRClientHandle hLPRClient );
	void setMainHandle(HWND);
public:
	CString m_strName;
	CIPAddressCtrl m_ipCtrl;
	CComboBox m_cmbType;
	CString m_strIP;
	CString m_strSerialNO;
	int m_nType;

	CString m_strGroupName;

	VzLPRClientHandle m_hLPRClient;
	
	BOOL m_bEnable;
	BOOL m_bDGResult;
	CString m_strGarage;
	CString m_strMatchDevice;
	HWND m_hMainWnd;

	VZ_OVZID_RESULT m_data;
	int m_nResultIndex;
	CString m_strNextGrage;
	afx_msg void OnBnClickedChkDgresultEnable();
};
