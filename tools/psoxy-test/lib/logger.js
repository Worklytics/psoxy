import _ from 'lodash';
import { addColors, createLogger, format, transports } from 'winston';

const config = {
  levels: {
    error: 0,
    success: 1,
    info: 2,
    entry: 3,
    verbose: 4,
  },
  colors: {
    error: 'bold red',
    success: 'bold green',
    info: 'bold blue',
    entry: 'white',
    verbose: 'white',
  },
}

export default function getLogger(verbose = false) {
  addColors(config.colors);

  return createLogger({
    level: 'verbose',
    levels: config.levels,
    transports: [
      new transports.Console({
        level: verbose ? 'verbose' : 'entry',
        format: format.combine(
          format.colorize(),
          format.errors({ stack: true }),
          format.printf((info) => {
            let message = `${info.level}: ${info.message}`;
            if (_.isObject(info.additional)) {
              message += `\n ${JSON.stringify(info.additional, undefined, 2)}`
            } else if (!_.isEmpty(info.additional)) {
              message += `\n ${info.additional}`;
            }
            return message;
          }),
        )
      }),
    ]
  });
}
