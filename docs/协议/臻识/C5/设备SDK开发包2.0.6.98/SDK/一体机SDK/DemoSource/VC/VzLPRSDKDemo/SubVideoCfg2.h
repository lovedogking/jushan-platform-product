#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>
#include "afxwin.h"
#include "afxcmn.h"

// CSubVideoCfg2 dialog

class CSubVideoCfg2 : public CDialog
{
	DECLARE_DYNAMIC(CSubVideoCfg2)

public:
	CSubVideoCfg2(CWnd* pParent = NULL);   // standard constructor
	virtual ~CSubVideoCfg2();

// Dialog Data
	enum { IDD = IDD_DLG_SUB_VIDEO_CFG_2 };

protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()

public:
	void SetLPRHandle( VzLPRClientHandle hLPRClient );

private:
	VzLPRClientHandle m_hLPRClient;
public:
	afx_msg void OnBnClickedOk();
	virtual BOOL OnInitDialog();

	CComboBox m_cmbExposureTime;
	CComboBox m_cmbImgPos;
	
	CString m_strBrt;
	CString m_strCst;
	CString m_strSat;
	CString m_strHue;

	CSliderCtrl m_sliderBrt;
	CSliderCtrl m_sliderCst;
	CSliderCtrl m_sliderSat;
	CSliderCtrl m_sliderHue;
	afx_msg void OnHScroll(UINT nSBCode, UINT nPos, CScrollBar* pScrollBar);
	afx_msg void OnCbnSelchangeCmbExposureTime();
	afx_msg void OnCbnSelchangeCmbImgPos();

	void Load( );
	void Init3730VideoParam();
	void Init8127VideoParam();
	CComboBox m_cmbDeNoiseMode;
	CComboBox m_cmbDeNoiseLenth;
	afx_msg void OnCbnSelchangeCmbDNMode();
	afx_msg void OnCbnSelchangeCmbDNStrength();
	CComboBox m_cmbStandard;
	int m_nHWType;
	afx_msg void OnCbnSelchangeCmbVideoStandard();
	CStatic m_staticDNMode;
	CStatic m_staticStanderd;
	CStatic m_staticDNStrength;
	CStatic m_staticImgPos;
	CStatic m_staticExposureTime;
	CButton m_btnRestore;
};
