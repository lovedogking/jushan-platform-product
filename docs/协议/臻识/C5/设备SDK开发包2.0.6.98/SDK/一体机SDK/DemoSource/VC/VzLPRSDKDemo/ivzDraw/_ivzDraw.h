//绘制线段的示例。
//来自网络。

#ifndef __IVZ_DRAW_H_
#define __IVZ_DRAW_H_

void ivzLine(unsigned char *image, int width, int height,
			 int widthStep, int channel, 
			 int x11, int y11, int x22, int y22,
			 const unsigned char color[]);

void ivzArrowRGB(unsigned char *pRGBA, int w, int h, int p,
				  int xHead, int yHead, int xTail, int yTail,
				  int arrowHeight,	//箭头三角形的高
				  int arrowNum,		//箭头向下叠加绘制个数（包括当前）
				  int minArrowGap,	//多个箭头叠加时之间的最小间隔
				  int bNeedLine,	//绘制出箭头到箭尾的线段
				  const unsigned char color[],
				  unsigned char bColor);
#endif
