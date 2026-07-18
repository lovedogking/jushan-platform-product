#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

// CDlgPlayVoice dialog

class CDlgPlayVoice : public CDialog
{
	DECLARE_DYNAMIC(CDlgPlayVoice)

public:
	CDlgPlayVoice(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgPlayVoice();

// Dialog Data
	enum { IDD = IDD_DLG_PLAY_VOICE };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	afx_msg void OnBnClickedBtnPlayVoice();
	void SetLPRHandle(VzLPRClientHandle hLPRClient);

private:
	VzLPRClientHandle m_hLPRClient;
	CString m_strContent;
};
