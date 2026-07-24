#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "Wave/Wave.h"

// CDlgVoice dialog

class CDlgVoice : public CDialog
{
	DECLARE_DYNAMIC(CDlgVoice)

public:
	CDlgVoice(CWnd* pParent = NULL);   // standard constructor
	virtual ~CDlgVoice();

// Dialog Data
	enum { IDD = IDD_DLG_VOICE };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );

private:
	VzLPRClientHandle m_hLPRClient;
public:
	virtual BOOL OnInitDialog();

private:
	int m_nTalkHandle;
	int m_nSoundHandle;

public:
	afx_msg void OnBnClickedBtnCall();
	afx_msg void OnBnClickedBtnPlayVoice();
	afx_msg void OnBnClickedBtnStopVoice();
	BOOL m_bChkWaveFile;

	WaveFile *m_pWaveFile;
	afx_msg void OnClose();
	CString m_strWavPath;
	afx_msg void OnBnClickedBtnBrowse();
};
