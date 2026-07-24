#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
// CDlgTcpTran dialog

class CDlgTcpTran : public CDialog
{
	DECLARE_DYNAMIC(CDlgTcpTran)

public:
	CDlgTcpTran(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgTcpTran();

// Dialog Data
	enum { IDD = IDD_DLG_TCP_TRAN };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	afx_msg LRESULT OnShowTcpDataMsg(WPARAM w,LPARAM l);
	DECLARE_MESSAGE_MAP()

public:
	virtual BOOL OnInitDialog();
	void SetLPRHandle(VzLPRClientHandle hLPRClient);

private:
	VzLPRClientHandle m_hLPRClient;
public:
	afx_msg void OnBnClickedBtnSendData();
	CString m_strTcpData;
	CString m_strTcpRecv;
};
