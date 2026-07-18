#pragma once
#include <VzLPRClientSDK.h>
#include <VzLPRFaceClientSDK.h>

#include "InteractViewer.h"
#include "afxdtctl.h"
#include "afxwin.h"

// DlgConfigRules dialog

class DlgConfigRules : public CDialog
{
	DECLARE_DYNAMIC(DlgConfigRules)

public:
	DlgConfigRules(VzLPRClientHandle handle, CWnd* pParent = NULL);   // standard constructor
	virtual ~DlgConfigRules();

	void iOnVideoFrame1(const VZ_LPRC_IMAGE_INFO *pFrame);
	void iOnVieweMouse1(IV_EVENT eEvent, int x, int y, int nId);

private:

    typedef enum
    {
        SEL_VL,
        SEL_ROI,
    }
    SEL_OBJ_TYPE;
    SEL_OBJ_TYPE m_eSelObjType; 

	static void iDrawLoops(unsigned char *pBuf,  int width, int height, int pitch, int nPF,
        const VZ_LPRC_VIRTUAL_LOOPS_EX &VLoops,
		int nIdxVLoopHover, int nIdxVLoopSelect, int nIdxPointHover);

    static void iDrawROI(unsigned char *pBuf,  int width, int height, int pitch, int nPF,
        const VZ_LPRC_ROI_EX &ROI, bool bHover, bool bSelect, int nIdxPointHoverROI);

	void iCtrl(VZ_LPRC_VIRTUAL_LOOPS_EX &struVLoops);

    void iCtrl(VZ_LPRC_ROI_EX &struROI);

	void iUpdateInfo(const VZ_LPRC_VIRTUAL_LOOP_EX *pStruVL);

    void iUpdateInfoROI(const VZ_LPRC_ROI_EX *pStruROI);

	void LoadLedCtrlParam( ); // 加载LED控制参数

	void Load3730LedCtrlParam();

	void ShowTimeCtrls(BOOL bShow);

	void LoadLedCtrlTimeParam(VZ_LPRC_CTRL_PARAM& led_ctrl_param);

	void ParseCtrlTime(const char* time_data, int& hour, int& minute);

private:
	VzLPRClientHandle m_hLPRC;
	int m_nPlayHandle;

	InteractViewer m_struInterV;
	volatile bool m_bEnableShow;

	VZ_LPRC_VIRTUAL_LOOPS_EX m_struVLoops;
	VZ_LPRC_VIRTUAL_LOOPS_EX m_struVLoopsBackup;

    bool m_bHaveROI;
    VZ_LPRC_ROI_EX m_struROI;
    VZ_LPRC_ROI_EX m_struROIBackup;

	unsigned m_uWinWidth;
	unsigned m_uWinHeight;

	typedef struct
	{
		IV_EVENT eEvent;
		int x;
		int y;
		bool m_bBtnDown;
		bool m_bBtnUp;
	}
	_IV_INFO;
	_IV_INFO m_struIVInfo;

	_IV_INFO m_struIVInfoLocal;

	VZ_LPRC_VERTEX m_struPointBackup;
	VZ_LPRC_VIRTUAL_LOOP_EX m_struLoopBackup;

	int m_nIdxVLoopHover;
	int m_nIdxVLoopSelect;

	int m_nIdxPointHover;

	bool m_bActiveUpdate;	//避免切换选择时的操作
    bool m_bActiveROIUpdate;
// Dialog Data
	enum { IDD = IDD_DLG_SET_RULE };
    
    VZ_LPRC_ROI_EX m_struROIBackupInModify;

    bool m_bROIHover;
    bool m_bROISelect;

    int m_nIdxVtxROIHover;

	int m_nHWType;

	int  m_nLastLEDLevel;
 VZ_DATE_TIME_INFO m_oDateTimeInfo;
protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support

	DECLARE_MESSAGE_MAP()
public:
	virtual BOOL OnInitDialog();
	virtual BOOL DestroyWindow();
	afx_msg void OnBnClickedBtnAddVloop();
	afx_msg void OnEnChangeEditRuleName();
	afx_msg void OnBnClickedBtnRmvVloop();
	afx_msg void OnBnClickedChkRuleEnable();
	afx_msg void OnBnClickedChkRuleShow();
	afx_msg void OnBnClickedBtnRuleSave();
	afx_msg void OnBnClickedBtnRuleCancel();
	afx_msg void OnBnClickedBtnResetAll();
	afx_msg void OnCbnSelchangeCmbProv();
	afx_msg void OnEnChangeEditTrigGap();
	afx_msg void OnCbnSelchangeCmbPassDir();
    afx_msg void OnEnChangeEditLpWidthMin();
    afx_msg void OnEnChangeEditLpWidthMax();
    afx_msg void OnBnClickedRadioSelectVl();
    afx_msg void OnBnClickedRadioSelectRoi();
    afx_msg void OnBnClickedBtnAddRoi();
    afx_msg void OnBnClickedBtnRmvRoi();
    afx_msg void OnBnClickedChkRoiEnable();
    afx_msg void OnBnClickedChkRoiShow();
	afx_msg void OnBnClickedBtnAutoFoucs();
	afx_msg void OnDtnDatetimechangeDtTime1End(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnDtnDatetimechangeDtTime2Start(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnDtnDatetimechangeDtTime2End(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnDtnDatetimechangeDtTime3Start(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnTimer(UINT_PTR nIDEvent);
	afx_msg void OnNMSetfocusDatetimepickerDay(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnNMKillfocusDatetimepickerDay(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnNMKillfocusDatetimepickerTime(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnNMSetfocusDatetimepickerTime(NMHDR *pNMHDR, LRESULT *pResult);
	afx_msg void OnBnClickedRadLedAuto();
	afx_msg void OnBnClickedRadLedOn();
	afx_msg void OnBnClickedRadLedOff();
	afx_msg void OnBnClickedBtnConfigDataTime();
	afx_msg void OnBnClickedBtnFastFocus();
	afx_msg void OnCbnSelchangeCmbLightLevel();
	afx_msg void OnBnClickedBtnSaveLedParam();
	virtual BOOL PreTranslateMessage(MSG* pMsg);

	CDateTimeCtrl m_DTPDevDay;
	CDateTimeCtrl m_dtpDevTime;

	bool    m_dateControlFoucs;
	bool    m_timeControlFoucs;
	

	void SetHWType(int type);

	CComboBox m_cbxLightCtrl1;
	CComboBox m_cbxLightCtrl2;
	CComboBox m_cbxLightCtrl3;
	CDateTimeCtrl m_dtTimeStart1;
	CDateTimeCtrl m_dtTimeEnd1;
	CDateTimeCtrl m_dtTimeStart2;
	CDateTimeCtrl m_dtTimeEnd2;
	CDateTimeCtrl m_dtTimeStart3;
	CDateTimeCtrl m_dtTimeEnd3;

	int m_nDeviceType;
	afx_msg void OnClose();
};
