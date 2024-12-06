import React from 'react'

import Plot from 'react-plotly.js'
import Store from "../../../util/Store";

class HeatmapWaterfallChart extends React.Component {
  constructor (props) {
    super(props)

    this.state = {
      data: props.data
    }
  }

  componentWillReceiveProps (nextProps) {
    this.setState({ data: nextProps.data })
  }

  render () {
    const data = this.state.data

    const colors = {}
    if (Store.get('dark_mode')) {
      colors.background = '#262626'
      colors.text = '#ffffff'
      colors.lines = '#2B2D42'
      colors.grid = '#8D99AE'
      colors.scale = [
        [0, 'rgb(38,38,38)'], [0.35, 'rgb(29,48,215)'],
        [0.5, 'rgb(190,190,190)'], [0.6, 'rgb(220,170,132)'],
        [0.7, 'rgb(230,145,90)'], [1, 'rgb(178,10,28)']
      ]
    } else {
      colors.background = '#f9f9f9'
      colors.text = '#111111'
      colors.lines = '#373737'
      colors.grid = '#e6e6e6'
      colors.scale =  [
        [0, 'rgb(249,249,249)'], [0.35, 'rgb(29,48,215)'],
        [0.5, 'rgb(190,190,190)'], [0.6, 'rgb(220,170,132)'],
        [0.7, 'rgb(230,145,90)'], [1, 'rgb(178,10,28)']
      ]
    }

    const finalData = [
      {
        z: data.z,
        x: data.x,
        y: data.y,
        type: 'heatmap',
        hovertemplate: this.props.hovertemplate,
        showscale: false,
        colorscale: colors.scale,
      }
    ]

    const marginLeft = this.props.customMarginLeft ? this.props.customMarginLeft : 60
    const marginRight = this.props.customMarginRight ? this.props.customMarginRight : 60
    const marginTop = this.props.customMarginTop ? this.props.customMarginTop : 25
    const marginBottom = this.props.customMarginBottom ? this.props.customMarginBottom : 50

    return (
            <Plot
                style={{ width: '100%', height: this.props.height }}
                data={finalData}
                layout={{
                  height: this.props.height,
                  width: this.props.width,
                  font: {
                    family: "'Nunito Sans', sans-serif",
                    size: 12,
                    color: colors.text
                  },
                  margin: { l: marginLeft, r: marginRight, b: marginBottom, t: marginTop, pad: 0 },
                  title: { text: this.props.title },
                  paper_bgcolor: colors.background,
                  plot_bgcolor: colors.lines,
                  showlegend: false,
                  dragmode: false,
                  clickmode: 'none',
                  hovermode: this.props.disableHover ? false : 'x',
                  xaxis: { visible: true, title: this.props.xaxistitle, zeroline: false, gridcolor: colors.background  },
                  yaxis: { visible: true, title: this.props.yaxistitle, zeroline: false, gridcolor: colors.background },
                  shapes: this.props.layers ? this.props.layers.shapes : null,
                  annotations: this.props.annotations ? this.props.annotations : []
                }}
                config={{
                  displayModeBar: false,
                  autosize: true,
                  responsive: true
                }}
            />
    )
  }
}

export default HeatmapWaterfallChart
